package com.example.productservice.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

@JsonIgnoreProperties(ignoreUnknown = true)
public class AuthValidationResult {

    private boolean success;
    private String message;
    private String userName;
    private Integer idUser;

    public AuthValidationResult() {
    }

    public AuthValidationResult(boolean success, String message) {
        this.success = success;
        this.message = message;
    }

    public AuthValidationResult(boolean success, String message, String userName, Integer idUser) {
        this.success = success;
        this.message = message;
        this.userName = userName;
        this.idUser = idUser;
    }

    public boolean isSuccess() {
        return success;
    }

    public void setSuccess(boolean success) {
        this.success = success;
    }

    public String getMessage() {
        return message;
    }

    public void setMessage(String message) {
        this.message = message;
    }

    public String getUserName() {
        return userName;
    }

    public void setUserName(String userName) {
        this.userName = userName;
    }

    public Integer getIdUser() {
        return idUser;
    }

    public void setIdUser(Integer idUser) {
        this.idUser = idUser;
    }
}
