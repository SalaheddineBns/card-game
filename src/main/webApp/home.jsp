<%@ page import="java.util.Map" %><%--
  Created by IntelliJ IDEA.
  User: acer swift 3
  Date: 20/04/2024
  Time: 20:24
  To change this template use File | Settings | File Templates.
--%>
<%@ page contentType="text/html;charset=UTF-8" language="java" %>
<html lang="en">
<head>
    <meta charset="UTF-8">
    <title>Connected Users</title>
    <script>
        var username = '<%= session.getAttribute("username") %>'; // Get username from HttpSession
    </script>
</head>
<body>
<h1>Connected Users</h1>
<h3>Please Select users that you need to invite</h3>
<ul id="userList">
    <%
        Map<String, HttpSession> connectedUsers = (Map<String, HttpSession>) session.getAttribute("connectedUsers");
        if (connectedUsers != null) {
            for (Map.Entry<String, HttpSession> entry : connectedUsers.entrySet()) {
                out.println("<li id='user-" + entry.getKey() + "'>User ID: " + entry.getKey() + " - Session: " + entry.getValue().getId() + "</li>");
            }
        }
    %>
</ul>
<input type="text" id="invitees" placeholder="Enter usernames, comma-separated">
<button onclick="sendInvitation()">Send Invitation</button>

<form action="logout" method="post">
    <button type="submit">Logout</button>
</form>
<script>
    <%--var userName = '<%= session.getAttribute("username") %>'; // Get username from session--%>
    <%--localStorage.setItem('username', userName);--%>

    <%--var wsUrl = 'ws://localhost:8080/LearnSocket_war_exploded/userStatus?username=' + encodeURIComponent(username);--%>
    <%--var ws = new WebSocket(wsUrl);--%>

    <%--ws.onmessage = function(event) {--%>
    <%--    // la logique ici est que le message provenant du backend contient un type d'action--%>
    <%--    var data = JSON.parse(event.data);--%>
    <%--    var userList = document.querySelector("ul");--%>

    <%--    if (data.action === "login") {--%>
    <%--        // Handle login--%>
    <%--        var newUserItem = document.createElement("li");--%>
    <%--        newUserItem.id = 'user-' + data.username; // Create a unique ID for removal if needed--%>
    <%--        newUserItem.textContent = "User ID: " + data.username + " - Session: " + data.sessionId;--%>
    <%--        userList.appendChild(newUserItem);--%>
    <%--    } else if (data.action === "logout") {--%>
    <%--        // Handle logout--%>
    <%--        console.log("logout")--%>
    <%--        var userItem = document.getElementById('user-'+data.username);--%>
    <%--        if (userItem) {--%>
    <%--            userList.removeChild(userItem);--%>
    <%--        }--%>
    <%--    }else if(data.action==="invitation"){--%>
    <%--        let sender = data.message.split(" ")[2]; // "name:"--%>
    <%--        sender = sender.replace(/:$/, ''); // Remove the colon at the end if it exists--%>

    <%--        if (confirm(event.data.message)) {--%>
    <%--            ws.send('response:' + sender + ':accept');--%>
    <%--        } else {--%>
    <%--            ws.send('response:' + sender + ':reject');--%>
    <%--        }        }--%>
    <%--    if (data.action === "redirect") {--%>
    <%--        localStorage.setItem('playersScore',JSON.stringify(data.scores))--%>
    <%--        // Optionally, store relevant game data if needed--%>
    <%--        //localStorage.setItem('gamePlayers', JSON.stringify(data.players));--%>

    <%--        //localStorage.setItem('selectedChars',JSON.stringify(data.selectedChars))--%>
    <%--        //localStorage.setItem('centerLetters',JSON.stringify(data.centerLetters))--%>
    <%--        localStorage.setItem('cartes',JSON.stringify(data.cartes))--%>
    <%--        localStorage.setItem('nbrTours',JSON.stringify(data.nbrTours))--%>

    <%--        localStorage.setItem('centreCartes',JSON.stringify(data.centreCartes))--%>

    <%--        // Redirect to the specified URL--%>
    <%--        window.location.href = data.url;--%>
    <%--    }--%>
    <%--};--%>

    <%--function sendInvitation() {--%>
    <%--    const invitees = document.getElementById('invitees').value;--%>
    <%--    ws.send('invite:' + invitees);--%>
    <%--}--%>

    var userName = '<%= session.getAttribute("username") %>'; // Get username from session--%>
    localStorage.setItem('username', userName);

    var wsUrl = 'ws://localhost:8080/LearnSocket_war_exploded/userStatus?username=' + encodeURIComponent(username);
    var ws = new WebSocket(wsUrl);

    ws.onmessage = function(event) {
        var data = JSON.parse(event.data);
        var userList = document.getElementById("userList");
        console.log(data)
        switch (data.action) {
            case "login":
                // Handle login by adding new user to list
                var newUserItem = document.createElement("li");
                newUserItem.id = 'user-' + data.username;
                newUserItem.textContent = data.username;
                userList.appendChild(newUserItem);
                break;
            case "logout":
                // Handle logout by removing user from list
                var userItem = document.getElementById('user-' + data.username);
                if (userItem) {
                    userList.removeChild(userItem);
                }
                break;
            case "invitation":
                // Handle invitation prompt
                var sender = data.sender;
                console.log(sender)
                if (confirm("Invitation from " + sender + ": accept or reject?")) {
                    ws.send(JSON.stringify({action: "response", sender: sender, response: "accept"}));
                } else {
                    ws.send(JSON.stringify({action: "response", sender: sender, response: "reject"}));
                }
                break;
            case "redirect":
                // Handle redirection with game preparation
                console.log(JSON.stringify(data.playerCards))
                localStorage.setItem('playersScore', JSON.stringify(data.scores));
                localStorage.setItem('playerCards', JSON.stringify(data.playerCards));
                localStorage.setItem('nbrTours', JSON.stringify(data.nbrTours));
                localStorage.setItem('centreCard', JSON.stringify(data.centreCard));
                window.location.href = data.url;
                break;
        }
    };

    function sendInvitation() {
        const invitees = document.getElementById('invitees').value.trim();
        if (invitees) {
            ws.send(JSON.stringify({action: "invite", invitees: invitees}));
        } else {
            alert('Please enter at least one username');
        }
    }
</script>
</body>
</html>
