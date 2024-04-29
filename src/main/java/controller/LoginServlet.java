package controller;

import com.example.learnsocket.UserStatusWebSocket;
import jakarta.servlet.ServletException;
import jakarta.servlet.annotation.WebServlet;
import jakarta.servlet.http.HttpServlet;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.servlet.http.HttpSession;

import java.io.IOException;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;


@WebServlet(name = "LoginServlet",urlPatterns = {"/login"})

public class LoginServlet extends HttpServlet {

    public static Map<String, HttpSession> sessions = new ConcurrentHashMap<>();

    @Override
    protected void doPost(HttpServletRequest req, HttpServletResponse resp) throws ServletException, IOException {
        for (Map.Entry<String, HttpSession> entry : sessions.entrySet()) {
            String userId = entry.getKey();
            HttpSession session = entry.getValue();
            System.out.println("User ID: " + userId + ", Session: " + session);
        }
        // Invalidate the existing session if any before creating a new one
        HttpSession oldSession = req.getSession(false);
        if (oldSession != null) {
            oldSession.invalidate();
        }
        HttpSession newSession = req.getSession(true);  // Create a new session for the user

        String userName= req.getParameter("username");
        String password= req.getParameter("password");
        if(userName.equals("admin") && password.equals("admin") || userName.equals("admine") && password.equals("admine")||
                userName.equals("salah") && password.equals("salah")
        ){
            HttpSession session = req.getSession();
            sessions.put(userName,newSession);
            session.setAttribute("connectedUsers", sessions);
            session.setAttribute("username", userName);  // Set username in session for later retrieval

            UserStatusWebSocket.broadcast(userName ,session.getId(),true);

            resp.sendRedirect("home.jsp");
        }else{
            resp.sendRedirect("index.jsp"); // redirect to login page if authentication fails
        }
    }
}
