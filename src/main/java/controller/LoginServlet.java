package controller;

import dao.JoueurDao;
import model.Joueur;
import webSocket.UserStatusWebSocket;
import jakarta.servlet.ServletException;
import jakarta.servlet.annotation.WebServlet;
import jakarta.servlet.http.HttpServlet;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.servlet.http.HttpSession;

import java.io.IOException;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;


@WebServlet(name = "LoginServlet",urlPatterns = {"/login"})

public class LoginServlet extends HttpServlet {

    private JoueurDao joueurDao;


    public static Map<String, HttpSession> sessions = new ConcurrentHashMap<>();

//    @Override
//    protected void doPost(HttpServletRequest req, HttpServletResponse resp) throws ServletException, IOException {
//        for (Map.Entry<String, HttpSession> entry : sessions.entrySet()) {
//            String userId = entry.getKey();
//            HttpSession session = entry.getValue();
//            System.out.println("User ID: " + userId + ", Session: " + session);
//        }
//        // Invalidate the existing session if any before creating a new one
//        HttpSession oldSession = req.getSession(false);
//        if (oldSession != null) {
//            oldSession.invalidate();
//        }
//        HttpSession newSession = req.getSession(true);  // Create a new session for the user
//
//        String userName= req.getParameter("username");
//        String password= req.getParameter("password");
//        if(userName.equals("admin") && password.equals("admin") || userName.equals("admine") && password.equals("admine")||
//                userName.equals("salah") && password.equals("salah")
//        ){
//            HttpSession session = req.getSession();
//            sessions.put(userName,newSession);
//            session.setAttribute("connectedUsers", sessions);
//            session.setAttribute("username", userName);  // Set username in session for later retrieval
//
//            UserStatusWebSocket.broadcast(userName ,session.getId(),true);
//
//            resp.sendRedirect("home.jsp");
//        }else{
//            resp.sendRedirect("index.jsp"); // redirect to login page if authentication fails
//        }
//    }

    @Override
    public void init() {
        joueurDao = new JoueurDao();
        // Initialize your DAO here, perhaps with a connection or other setup if necessary.
    }
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
    List<Joueur> joueurs = joueurDao.findAll(); // Récupère tous les joueurs pour trouver une correspondance
    for (Joueur joueur : joueurs) {
        if (joueur.getPseudo().equals(userName) && joueur.getMotDePasse().equals(password)) {

            // Authentification réussie, redirection vers une page sécurisée (par ex. home.jsp)
            HttpSession session = req.getSession();
            session.setAttribute("currentJoueur",joueur);
            session.setAttribute("pseudo", joueur.getPseudo()); // Stocker l'utilisateur dans la session
            session.setAttribute("age",joueur.getÂge());
            session.setAttribute("nbrPartieJouées",joueur.getNombrePartiesJouées());
            session.setAttribute("genre",joueur.getGenre());
            session.setAttribute("nbrVictoire",joueur.getNombreVictoires());
            session.setAttribute("ratioClicRapide",joueur.getRatioClicRapide());
            session.setAttribute("ratioClicReussi",joueur.getRatioClicRéussi());
            session.setAttribute("scoreMoy",joueur.getScoreMoyen());
            sessions.put(userName,newSession);
            session.setAttribute("connectedUsers", sessions);
            session.setAttribute("username", userName);  // Set username in session for later retrieval
            UserStatusWebSocket.broadcast(userName ,session.getId(),true);
            resp.setStatus(HttpServletResponse.SC_OK);
            resp.setContentType("application/json");
            resp.sendRedirect("home.jsp");
            return;
        }

    }
    req.setAttribute("error", "Invalid username or password");
    resp.sendRedirect("index.jsp"); // redirect to login page if authentication fails




}

}
