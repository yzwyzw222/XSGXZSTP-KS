<%@ page contentType="text/html;charset=UTF-8" language="java" %>
<%@ page import="com.example.Shiyan2.Message" %>
<%@ page import="java.util.ArrayList" %>
<html>
<head>
  <title>查看留言板</title>
  <style>
    table, th, td { border: 1px solid black; border-collapse: collapse; padding: 5px; }
  </style>
</head>
<body>
<h3>所有留言</h3>
<%
  // 未登录则跳转到登录页
  if(session.getAttribute("username") == null){
    response.sendRedirect("login.jsp");
    return;
  }

  ArrayList<Message> list = (ArrayList<Message>) application.getAttribute("messageList");
  if(list == null || list.size() == 0){
    out.println("<p>暂无留言</p>");
    return;
  }
%>

<table>
  <tr>
    <th>序号</th>
    <th>姓名</th>
    <th>标题</th>
    <th>留言内容</th>
    <th>留言时间</th>
  </tr>
  <% for(int i=0; i<list.size(); i++){
    Message msg = list.get(i);
  %>
  <tr>
    <td>第<%= i+1 %>个</td>
    <td><%= msg.getUsername() %></td>
    <td><%= msg.getTitle() %></td>
    <td><%= msg.getContent() %></td>
    <td><%= msg.getTime() %></td>
  </tr>
  <% } %>
</table>
<br>
<a href="../../../../web/Shiyan2/msgBoard.jsp">返回留言板</a>
</body>
</html>