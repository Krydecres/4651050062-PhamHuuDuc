import base64
import hashlib
import hmac
import json
import re
import time
from pathlib import Path

ROOT = Path(__file__).resolve().parents[2]
OUT = Path(__file__).resolve().parent
items = []


def add(name, method, path, status, body=None, auth=True, extra='', port=8081, headers=None):
    hs = []
    if auth:
        hs.append({'key': 'Authorization', 'value': 'Bearer {{jwt_token}}'})
    if body is not None:
        hs.append({'key': 'Content-Type', 'value': 'application/json'})
    hs.extend(headers or [])
    req = {'method': method, 'header': hs, 'url': f'http://localhost:{port}{path}'}
    if body is not None:
        req['body'] = {'mode': 'raw', 'raw': body if isinstance(body, str) else json.dumps(body, ensure_ascii=False), 'options': {'raw': {'language': 'json'}}}
    script = f'pm.test("HTTP {status}", () => pm.response.to.have.status({status}));\n'
    script += 'pm.test("JSON response", () => pm.expect(pm.response.headers.get("Content-Type")).to.include("application/json"));\n'
    script += 'let j; try { j = pm.response.json(); } catch(e) {}\n'
    script += f'pm.test("success flag", () => pm.expect(j.success).to.eql({str(status < 400).lower()}));\n'
    script += extra
    item = {'name': f'{len(items)+1:02}. {name}', 'request': req, 'event': [{'listen': 'test', 'script': {'type': 'text/javascript', 'exec': script.splitlines()}}]}
    items.append(item)
    return item


valid = {'name': 'BTH3_QA_{{run_id}}', 'description': 'Kiểm thử sản phẩm tiếng Việt', 'price': 12345.67, 'quantity': 7}
updated = dict(valid, name='BTH3_QA_{{run_id}}_UPDATED', description='Đã cập nhật', price=23456.78, quantity=9)
add('Wrong login rejected', 'POST', '/', 401, {'userName': 'admin', 'password': 'wrong_qa_password'}, False, port=8080)
add('Login and save JWT', 'POST', '/', 200, {'userName': 'admin', 'password': '123456'}, False, '''
pm.test("JWT returned", () => pm.expect(j.token).to.be.a("string").and.not.empty);
for (const key of ["product_id","created_at","original_products","overflow_id","fraction_qty_id","limit_id","zero_id","scale_id","scale_response_price"]) pm.collectionVariables.unset(key);
if (j && j.token) { pm.collectionVariables.set("jwt_token", j.token); pm.collectionVariables.set("run_id", String(Date.now())); pm.collectionVariables.set("tampered_token", j.token.split('.').slice(0,2).join('.') + '.AAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAA'); }
''', port=8080)
add('Auth service validates JWT', 'GET', '/auth', 200, port=8080, extra='pm.test("Identity", () => pm.expect(j.userName).to.eql("admin"));')
for method, path in [('GET', '/products'), ('GET', '/products/2147483647'), ('POST', '/products'), ('PUT', '/products/2147483647'), ('DELETE', '/products/2147483647')]:
    add(f'{method} requires authentication', method, path, 401, valid if method in ('POST','PUT') else None, False)
add('Fake token rejected', 'GET', '/products', 401, auth=False, headers=[{'key':'Authorization','value':'Bearer {{invalid_token}}'}])
add('Tampered signature rejected', 'GET', '/products', 401, auth=False, headers=[{'key':'Authorization','value':'Bearer {{tampered_token}}'}])
secret_source = (ROOT / 'src/main/java/com/example/demo/service/JwtService.java').read_text(encoding='utf-8')
secret = base64.b64decode(re.search(r'SECRET_KEY = "([^"]+)"', secret_source).group(1))
enc = lambda b: base64.urlsafe_b64encode(b).rstrip(b'=')
payload = enc(json.dumps({'sub':'admin','idUser':1,'iat':int(time.time())-7200,'exp':int(time.time())-3600}).encode())
message = enc(b'{"alg":"HS256","typ":"JWT"}') + b'.' + payload
expired = (message + b'.' + enc(hmac.new(secret, message, hashlib.sha256).digest())).decode()
expired_item = add('Expired signed token rejected', 'GET', '/products', 401, auth=False, headers=[{'key':'Authorization','value':'Bearer {{expired_token}}'}])
expired_item['event'].insert(0, {'listen':'prerequest','script':{'type':'text/javascript','exec':['pm.collectionVariables.set("expired_token", '+json.dumps(expired.split('.'))+'.join("."));']}})
add('List products and capture original data', 'GET', '/products', 200, extra='''
pm.test("List is array", () => pm.expect(j.data).to.be.an("array"));
if (j && Array.isArray(j.data)) pm.collectionVariables.set("original_products", JSON.stringify(j.data));
''')
add('Create a temporary product', 'POST', '/products', 201, valid, extra='''
if (j && j.data && j.data.id) { pm.collectionVariables.set("product_id", j.data.id); pm.collectionVariables.set("created_at", j.data.createdAt); }
pm.test("Created values and generated fields", () => { pm.expect(j.data.id).to.be.a("number"); pm.expect(j.data.name).to.eql(pm.variables.replaceIn("BTH3_QA_{{run_id}}")); pm.expect(j.data.description).to.eql("Kiểm thử sản phẩm tiếng Việt"); pm.expect(j.data.price).to.eql(12345.67); pm.expect(j.data.quantity).to.eql(7); pm.expect(Date.parse(j.data.createdAt)).not.to.be.NaN; pm.expect(Date.parse(j.data.updatedAt)).not.to.be.NaN; });
''')
add('Read the newly created product', 'GET', '/products/{{product_id}}', 200, extra='pm.test("Created values persisted", () => { pm.expect(j.data.id).to.eql(Number(pm.collectionVariables.get("product_id"))); pm.expect(j.data.price).to.eql(12345.67); pm.expect(j.data.quantity).to.eql(7); });')
add('List contains the temporary product', 'GET', '/products', 200, extra='pm.test("Created product appears", () => pm.expect(j.data.some(p => p.id === Number(pm.collectionVariables.get("product_id")))).to.eql(true));')
add('Update only the temporary product', 'PUT', '/products/{{product_id}}', 200, updated, extra='''
pm.test("Updated values", () => { pm.expect(j.data.price).to.eql(23456.78); pm.expect(j.data.quantity).to.eql(9); pm.expect(j.data.name).to.eql(pm.variables.replaceIn("BTH3_QA_{{run_id}}_UPDATED")); });
pm.test("Creation time preserved; update time advances", () => { pm.expect(Date.parse(j.data.createdAt)).to.eql(Date.parse(pm.collectionVariables.get("created_at"))); pm.expect(Date.parse(j.data.updatedAt)).to.be.above(Date.parse(j.data.createdAt)); });
''')
add('Read confirms update persisted', 'GET', '/products/{{product_id}}', 200, extra='pm.test("Updated data persisted", () => { pm.expect(j.data.price).to.eql(23456.78); pm.expect(j.data.quantity).to.eql(9); pm.expect(j.data.description).to.eql("Đã cập nhật"); });')
for name, body in [
    ('Blank name', dict(valid, name='   ')),
    ('Missing name', {k:v for k,v in valid.items() if k!='name'}),
    ('Missing price', {k:v for k,v in valid.items() if k!='price'}),
    ('Missing quantity', {k:v for k,v in valid.items() if k!='quantity'}),
    ('Negative price', dict(valid, price=-1)),
    ('Negative quantity', dict(valid, quantity=-1)),
    ('Name exceeds 255 characters', dict(valid, name='x'*256)),
    ('Malformed JSON', '{"name":'),
    ('Wrong price type', dict(valid, price='abc')),
]:
    add(name+' rejected', 'POST', '/products', 400, body)
add('Non-numeric ID rejected', 'GET', '/products/abc', 400)
add('Unknown product returns 404', 'GET', '/products/2147483647', 404)
add('Update unknown product returns 404', 'PUT', '/products/2147483647', 404, valid)
add('Delete unknown product returns 404', 'DELETE', '/products/2147483647', 404)
add('Unsupported PATCH returns 405', 'PATCH', '/products/{{product_id}}', 405, valid)
it=add('Unsupported media type returns 415', 'POST', '/products', 415, valid)
it['request']['header']=[{'key':'Authorization','value':'Bearer {{jwt_token}}'},{'key':'Content-Type','value':'text/plain'}]
for name, body, status, variable, extra in [
    ('Price overflow should return 400', dict(valid, price=100000000), 400, 'overflow_id', ''),
    ('Fractional quantity should be rejected', dict(valid, quantity=1.9), 400, 'fraction_qty_id', ''),
    ('Price at DECIMAL limit', dict(valid, price=99999999.99, quantity=2147483647), 201, 'limit_id', 'pm.test("Maximum values preserved", () => { pm.expect(j.data.price).to.eql(99999999.99); pm.expect(j.data.quantity).to.eql(2147483647); });'),
    ('Zero price and quantity allowed', dict(valid, price=0, quantity=0, description=None), 201, 'zero_id', 'pm.test("Zero and null description accepted", () => { pm.expect(j.data.price).to.eql(0); pm.expect(j.data.quantity).to.eql(0); pm.expect(j.data.description).to.eql(null); });'),
    ('Extra price decimals rejected', dict(valid, price=1.999), 400, 'scale_id', ''),
]:
    add(name, 'POST', '/products', status, body, extra=f'if (j && j.data && j.data.id) pm.collectionVariables.set("{variable}", j.data.id);\n'+extra)
add('Rejected extra decimals leave stored price unchanged', 'GET', '/products/{{product_id}}', 200, extra='pm.test("Stored price unchanged", () => pm.expect(j.data.price).to.eql(23456.78));')
for name, body in [('Update price overflow rejected',dict(updated,price=100000000)),('Update extra price decimals rejected',dict(updated,price=1.999)),('Update fractional quantity rejected',dict(updated,quantity=1.9))]:
    add(name,'PUT','/products/{{product_id}}',400,body)
it=add('Update unsupported media type returns 415','PUT','/products/{{product_id}}',415,updated)
it['request']['header']=[{'key':'Authorization','value':'Bearer {{jwt_token}}'},{'key':'Content-Type','value':'text/plain'}]
add('Invalid update rejected', 'PUT', '/products/{{product_id}}', 400, dict(updated, quantity=-10))
add('Invalid update leaves prior values intact', 'GET', '/products/{{product_id}}', 200, extra='pm.test("No partial update", () => { pm.expect(j.data.price).to.eql(23456.78); pm.expect(j.data.quantity).to.eql(9); });')
add('Delete the temporary CRUD product', 'DELETE', '/products/{{product_id}}', 200)
add('Deleted product returns 404', 'GET', '/products/{{product_id}}', 404)
add('Repeated delete returns 404', 'DELETE', '/products/{{product_id}}', 404)
for variable in ['overflow_id', 'fraction_qty_id', 'limit_id', 'zero_id', 'scale_id']:
    it=add('Cleanup '+variable, 'DELETE', '/products/{{'+variable+'}}', 200)
    it['event'].insert(0, {'listen':'prerequest', 'script':{'type':'text/javascript','exec':[f'if (!pm.collectionVariables.get("{variable}")) pm.execution.skipRequest();']}})
add('Original products remain unchanged', 'GET', '/products', 200, extra='pm.test("Exact original dataset restored", () => pm.expect(j.data).to.deep.eql(JSON.parse(pm.collectionVariables.get("original_products"))));')
collection = {
    'info': {'name':'BTH3 - Kiem thu sau sua loi', 'schema':'https://schema.getpostman.com/json/collection/v2.1.0/collection.json', 'description':'CRUD and authentication checks, plus POST/PUT regression checks for price limits, decimal precision, integer quantity and unsupported media types. Writes affect only newly created BTH3_QA products; original products are checked unchanged.'},
    'item':items,
    'variable':[{'key':k,'value':'fake_qa_token' if k=='invalid_token' else ''} for k in ['jwt_token','product_id','run_id','tampered_token','expired_token','invalid_token','created_at','original_products','overflow_id','fraction_qty_id','limit_id','zero_id','scale_id','scale_response_price']]
}
OUT.mkdir(parents=True,exist_ok=True)
(OUT/'BTH3_Verification.postman_collection.json').write_text(json.dumps(collection,ensure_ascii=False,indent=2),encoding='utf-8')
print(f'Generated {len(items)} requests')
