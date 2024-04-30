package controller;

import dao.JoueurDao;
import jakarta.servlet.ServletException;
import jakarta.servlet.annotation.WebServlet;
import jakarta.servlet.http.HttpServlet;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import model.Joueur;

import java.io.IOException;

@WebServlet(name = "RegisterServlet",urlPatterns = {"/register"})
public class RegisterServlet extends HttpServlet {
    private JoueurDao joueurDao;


    @Override
    public void init() {
        joueurDao = new JoueurDao();
        // Initialize your DAO here, perhaps with a connection or other setup if necessary.
    }

    @Override
    protected void doPost(HttpServletRequest req, HttpServletResponse resp) throws ServletException, IOException {
        // Réception des paramètres du formulaire
        String pseudo = req.getParameter("username");
        String genre = req.getParameter("genre"); // Assurez-vous que le modèle Joueur peut stocker cette information
        int age = Integer.parseInt(req.getParameter("age")); // Assurez-vous de gérer correctement les exceptions liées au formatage
        String motDePasse = req.getParameter("password");
        String confirmationMotDePasse = req.getParameter("confirm_password");
        if (!motDePasse.equals(confirmationMotDePasse)) {
            req.setAttribute("errorMessage", "Les mots de passe ne correspondent pas.");
            req.getRequestDispatcher("register.jsp").forward(req, resp);
            return; // Important pour arrêter l'exécution ici
        }
        Joueur joueur = new Joueur();
        joueur.setPseudo(pseudo);
        joueur.setGenre(genre); // Assurez-vous que cette propriété existe dans votre modèle
        joueur.setÂge(age);
        joueur.setMotDePasse(motDePasse);
        joueur.setNombreVictoires(0);
        joueur.setNombrePartiesJouées(0);
        joueur.setRatioClicRapide(0.0);
        joueur.setRatioClicRéussi(0.0);
        joueur.setScoreMoyen(0.0);



        try{
            joueurDao.save(joueur);
            req.setAttribute("successMessage", "Account creation successful!");
            //req.getRequestDispatcher("register.jsp").forward(req, resp); // Ou toute autre vue JSP pertinente
            resp.sendRedirect("home.jsp?successMessage=Account+creation+successful%21");

            //resp.sendRedirect("login.jsp"); // Redirection vers la page de connexion après l'inscription

        }catch (Exception e){
            throw new ServletException("Erreur Saving Joueur",e);
        }
    }
}
