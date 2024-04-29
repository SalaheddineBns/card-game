<%--
  Created by IntelliJ IDEA.
  User: acer swift 3
  Date: 26/04/2024
  Time: 23:52
  To change this template use File | Settings | File Templates.
--%>
<%@ page contentType="text/html;charset=UTF-8" language="java" %>
<!DOCTYPE html>
<html lang="en">
<head>
    <meta charset="UTF-8">
    <title>Winner Announcement</title>
    <style>
        body {
            font-family: Arial, sans-serif;
            display: flex;
            justify-content: center;
            align-items: center;
            height: 100vh;
            background-color: #f0f0f0;
            margin: 0;
        }
        .announcement {
            display: none;
            font-size: 2em;
            background-color: #fff;
            padding: 20px;
            border-radius: 10px;
            box-shadow: 0 4px 6px rgba(0,0,0,0.1);
            text-align: center;
        }
    </style>
</head>
<body>
<div id="announcement" class="announcement">
    <p>The winneeeerr isss: <span id="winnerName"></span></p>
    <p>And the looserrr isss: <span id="loserName"></span></p>
</div>

<script>
    // Function to display the winner and loser
    function displayWinnerAndLoser() {
        const winner = localStorage.getItem('winner'); // Retrieve the winner from local storage
        const loser = localStorage.getItem('loser'); // Retrieve the loser from local storage

        // Set the text content for winner and loser
        document.getElementById('winnerName').textContent = winner;
        document.getElementById('loserName').textContent = loser;

        // Show the announcement div
        document.getElementById('announcement').style.display = 'block';
    }

    // Set a timeout to delay the announcement for 3 seconds
    setTimeout(displayWinnerAndLoser, 3000);
</script>
</body>
</html>
