<%@ page contentType="text/html;charset=UTF-8" language="java" %>
<%@ page import="com.example.Shiyan2.Message" %>
<%@ page import="java.util.ArrayList" %>
<html>
<head>
    <title>留言板</title>
</head>
<body>
<%
    // 从Session获取用户名，未登录则跳转到登录页
    String username = (String) session.getAttribute("username");
    if(username == null){
        response.sendRedirect("login.jsp");
        return;
    }

    // 初始化application中的留言列表（如果不存在）
    if(application.getAttribute("messageList") == null){
        application.setAttribute("messageList", new ArrayList<Message>());
    }

    // 处理留言提交请求
    request.setCharacterEncoding("UTF-8");
    String title = request.getParameter("title");
    String content = request.getParameter("content");
    if(title != null && content != null){
        Message msg = new Message(username, title, content, new java.util.Date());
        ArrayList<Message> list = (ArrayList<Message>) application.getAttribute("messageList");
        list.add(msg);
        application.setAttribute("messageList", list);
        out.println("<script>alert('留言提交成功！')</script>");
    }
%>

<h3>留言板</h3>
<form action="msgBoard.jsp" method="post">
    <table>
        <tr>
            <td>请输入姓名：</td>
            <td><input type="text" name="username" value="<%= username %>" readonly></td>
        </tr>
        <tr>
            <td>请输入标题：</td>
            <td><input type="text" name="title" required></td>
        </tr>
        <tr>
            <td>请输入留言：</td>
            <td><textarea name="content" rows="5" cols="30" required></textarea></td>
        </tr>
        <tr>
            <td colspan="2">
                <input type="submit" value="提交留言">
                <a href="showMessages.jsp"><input type="button" value="查看留言板"></a>
            </td>
        </tr>
    </table>
</form>
</body>
</html>