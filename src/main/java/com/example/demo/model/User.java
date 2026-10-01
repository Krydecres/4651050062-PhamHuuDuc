package com.example.demo.model;

/**
 * Đại diện cho Bảng người dùng (User) theo Phụ lục đề bài:
 * - IdUser INT (PRIMARY KEY)
 * - UserName (hoặc email) VARCHAR(255)
 * - Password VARCHAR(255)
 * - Token VARCHAR(255)
 */
public class User {

    private Integer idUser;
    private String userName;
    private String password;
    private String token;

    public User() {
    }

    public User(Integer idUser, String userName, String password, String token) {
        this.idUser = idUser;
        this.userName = userName;
        this.password = password;
        this.token = token;
    }

    public Integer getIdUser() {
        return idUser;
    }

    public void setIdUser(Integer idUser) {
        this.idUser = idUser;
    }

    public String getUserName() {
        return userName;
    }

    public void setUserName(String userName) {
        this.userName = userName;
    }

    public String getPassword() {
        return password;
    }

    public void setPassword(String password) {
        this.password = password;
    }

    public String getToken() {
        return token;
    }

    public void setToken(String token) {
        this.token = token;
    }
}
