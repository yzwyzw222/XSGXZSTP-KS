package com.example.web.Shiyan2;

import java.util.Date;

public class Message {
    private String username;  // 姓名
    private String title;     // 标题
    private String content;   // 留言内容
    private Date time;        // 留言时间

    // 构造方法
    public Message(String username, String title, String content, Date time) {
        this.username = username;
        this.title = title;
        this.content = content;
        this.time = time;
    }

    // Getter/Setter方法
    public String getUsername() { return username; }
    public void setUsername(String username) { this.username = username; }
    public String getTitle() { return title; }
    public void setTitle(String title) { this.title = title; }
    public String getContent() { return content; }
    public void setContent(String content) { this.content = content; }
    public Date getTime() { return time; }
    public void setTime(Date time) { this.time = time; }
}