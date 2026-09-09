<%@ page contentType="text/html;charset=UTF-8" language="java" %>
<%@ page import="javax.servlet.http.Cookie" %>
<html>
<head>
  <title>登录验证</title>
</head>
<body>
<%
  // 解决中文乱码问题
  request.setCharacterEncoding("UTF-8");

  // 1. 获取表单提交的用户名和密码
  String username = request.getParameter("username");
  String password = request.getParameter("password");
  String remember = request.getParameter("remember");

  // 简单参数校验
  if(username == null || username.trim().isEmpty()){
    out.println("<script>alert('用户名不能为空！');history.back();</script>");
    return;
  }

  // 2. 保存用户名到Session
  session.setAttribute("username", username);

  // 3. 处理记住密码的Cookie
  if("1".equals(remember)){
    // 创建Cookie，有效期7天
    Cookie userCookie = new Cookie("username", username);
    Cookie pwdCookie = new Cookie("password", password);
    userCookie.setMaxAge(7*24*60*60);
    pwdCookie.setMaxAge(7*24*60*60);
    response.addCookie(userCookie);
    response.addCookie(pwdCookie);
  } else {
    // 不记住则删除Cookie
    Cookie userCookie = new Cookie("username", "");
    Cookie pwdCookie = new Cookie("password", "");
    userCookie.setMaxAge(0);
    pwdCookie.setMaxAge(0);
    response.addCookie(userCookie);
    response.addCookie(pwdCookie);
  }

  // 4. 跳转到留言板页面
  response.sendRedirect("msgBoard.jsp");
%>
</body>
</html>