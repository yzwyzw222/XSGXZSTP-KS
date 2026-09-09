<%@ page contentType="text/html;charset=UTF-8" language="java" %>
<%@ page import="javax.servlet.http.Cookie" %>
<%@ page import="org.owasp.encoder.Encode" %>
<html>
<head>
    <title>用户登录</title>
</head>
<body>
<h3>用户登录</h3>
<%
    // 读取Cookie中保存的用户名（仅记住用户名，不记住密码）
    String username = "";
    Cookie[] cookies = request.getCookies();
    if(cookies != null){
        for(Cookie c : cookies){
            if("username".equals(c.getName())){
                username = Encode.forHtml(c.getValue());
            }
        }
    }
%>
<form action="loginCheck.jsp" method="post">
    <table>
        <tr>
            <td>用户名：</td>
            <td><input type="text" name="username" value="<%= username %>"></td>
        </tr>
        <tr>
            <td>密码：</td>
            <td><input type="password" name="password"></td>
        </tr>
        <tr>
            <td colspan="2">
                <input type="checkbox" name="remember" value="1">记住用户名
            </td>
        </tr>
        <tr>
            <td>
                <input type="submit" value="登录">
            </td>
            <td>
                <input type="reset" value="重置">
            </td>
        </tr>
    </table>
</form>
</body>
</html>
