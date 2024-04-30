package controller;

import dao.JoueurDao;
import jakarta.servlet.ServletException;
import jakarta.servlet.annotation.WebServlet;
import jakarta.servlet.http.HttpServlet;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.servlet.http.HttpSession;
import model.Joueur;

import java.io.IOException;
import java.io.PrintWriter;

@WebServlet(name = "ProfileServlet",urlPatterns = {"/profileServlet"})

public class ProfileServlet extends HttpServlet {


   JoueurDao joueurDao;

   @Override
   public void init() throws ServletException {
      super.init();
      joueurDao = new JoueurDao();
   }

   @Override
   protected void doGet(HttpServletRequest req, HttpServletResponse resp) throws ServletException, IOException {
      super.doGet(req, resp);
      processRequest(req, resp);
   }

   protected void processRequest(HttpServletRequest request, HttpServletResponse response) throws ServletException, IOException {
      try (PrintWriter out = response.getWriter()) {

         String pseudo = (String) request.getSession().getAttribute("pseudo");
         JoueurDao joueurDao = new JoueurDao();
         Joueur joueur = joueurDao.findByPseudo(pseudo);

         out.println("<!DOCTYPE html>");
         out.println("<html>");
         out.println("<head>");
         out.println("<title>Servlet WebController</title>");
         out.println("</head>");
         out.println("<body>");
         out.println("<h1>" + joueur.getPseudo() + "</h1>");
         out.println("<h2>" + joueur.getÂge() + "</h2>");
         out.println("</body>");
         out.println("</html>");
      } catch (IOException e) {
      }
   }

   @Override
   protected void doPost(HttpServletRequest req, HttpServletResponse resp) throws ServletException, IOException {
      try {
         String pseudo = req.getParameter("username");
         String genre = req.getParameter("genre");
         int age = Integer.parseInt(req.getParameter("age"));

         HttpSession s = req.getSession();
         Joueur joueur = (Joueur) s.getAttribute("currentJoueur");
         joueur.setGenre(genre);
         joueur.setÂge(age);

         joueurDao.update(joueur);

         s.setAttribute("currentJoueur",joueur);
         s.setAttribute("pseudo", joueur.getPseudo()); // Stocker l'utilisateur dans la session
         s.setAttribute("age",joueur.getÂge());
         s.setAttribute("nbrPartieJouées",joueur.getNombrePartiesJouées());
         s.setAttribute("genre",joueur.getGenre());
         // In your ProfileServlet's doPost method after successful update
         s.setAttribute("updateStatus", "Modification réussie!");


         resp.sendRedirect("profile.jsp");
      } catch (Exception e) {
         // Gérer l'exception ici, potentiellement en logguant l'erreur
         req.setAttribute("error", "Une erreur est survenue : " + e.getMessage());
         req.getRequestDispatcher("errorPage.jsp").forward(req, resp);
      }
   }

}

