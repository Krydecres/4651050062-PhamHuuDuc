package com.example.demo.controller;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class HelloController {

    /**
     * API in dòng "Hello World" ở bài thực hành số 1,
     * được bảo vệ bởi Middleware (JwtAuthFilter)
     */
    @GetMapping({"/hello", "/helloworld"})
    public String hello() {
        return "Hello World!";
    }
}