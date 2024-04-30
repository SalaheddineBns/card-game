<%@ page contentType="text/html;charset=UTF-8" language="java" %>
<!DOCTYPE html>
<html>
<head>
    <title>Profile</title>
    <link rel="stylesheet" href="https://cdn.jsdelivr.net/npm/bootstrap@4.5.3/dist/css/bootstrap.min.css"
          integrity="sha384-TX8t27EcRE3e/ihU7zmQxVncDAy5uIKz4rEkgIXeMed4M0jlfIDPvg6uqKI2xXr2" crossorigin="anonymous">
    <script src="https://code.jquery.com/jquery-3.5.1.min.js"></script>
    <script>
        $(document).ready(function() {
            var username = '<%= (String) session.getAttribute("username") %>';
            console.log(username)
            $.ajax({
                url: '/LearnSocket_war_exploded/profileServlet',
                type: 'GET',
                data: {username: username},
                success: function(joueur) {
                    $('#fullName').val(joueur.pseudo);
                    $('#age').val(joueur.âge);
                    $('#genre').val(joueur.genre);
                    $('#nbrPart').val(joueur.nombrePartiesJouées);
                    $('#nbrVict').val(joueur.nombreVictoires);
                    $('#moy').val(joueur.scoreMoyen);
                    $('#clcReussi').val(joueur.ratioClicRéussi);
                    $('#clcRapide').val(joueur.ratioClicRapide);


                    console.log('Data loaded successfully');
                },
                error: function() {
                    alert('Error loading data');
                }
            });
        });
    </script>
    <!-- Ajoutez ici vos références CSS ou tout autre élément de tête nécessaire -->
</head>
<style>
    body {
        margin: 0;
        padding-top: 40px;
        color: #2e323c;
        background: #f5f6fa;
        position: relative;
        height: 100%;
    }

    .account-settings .user-profile {
        margin: 0 0 1rem 0;
        padding-bottom: 1rem;
        text-align: center;
    }

    .account-settings .user-profile .user-avatar {
        margin: 0 0 1rem 0;
    }

    .account-settings .user-profile .user-avatar img {
        width: 90px;
        height: 90px;
        -webkit-border-radius: 100px;
        -moz-border-radius: 100px;
        border-radius: 100px;
    }

    .account-settings .user-profile h5.user-name {
        margin: 0 0 0.5rem 0;
    }

    .account-settings .user-profile h6.user-email {
        margin: 0;
        font-size: 0.8rem;
        font-weight: 400;
        color: #9fa8b9;
    }

    .account-settings .about {
        margin: 2rem 0 0 0;
        text-align: center;
    }

    .account-settings .about h5 {
        margin: 0 0 15px 0;
        color: #007ae1;
    }

    .account-settings .about p {
        font-size: 0.825rem;
    }

    .form-control {
        border: 1px solid #cfd1d8;
        -webkit-border-radius: 2px;
        -moz-border-radius: 2px;
        border-radius: 2px;
        font-size: .825rem;
        background: #ffffff;
        color: #2e323c;
    }

    .card {
        background: #ffffff;
        -webkit-border-radius: 5px;
        -moz-border-radius: 5px;
        border-radius: 5px;
        border: 0;
        margin-bottom: 1rem;
    }
</style>
<body>
<%--<%--%>
<%--    // Supposons que ces attributs soient déjà définis dans votre session--%>
    <% String pseudo = (String) session.getAttribute("pseudo"); %>
<%--    int age = (int) session.getAttribute("age");--%>
<%--    int nbrPartieJouées = (int) session.getAttribute("nbrPartieJouées");--%>
<%--    String genre = (String) session.getAttribute("genre");--%>
<%--    int nbrVictoire = (int) session.getAttribute("nbrVictoire");--%>
<%--    double ratioClicRapide = (double) session.getAttribute("ratioClicRapide");--%>
<%--    double ratioClicReussi = (double) session.getAttribute("ratioClicReussi");--%>
<%--    double scoreMoy = (double) session.getAttribute("scoreMoy");--%>

<%--%>--%>
<div class="container">
    <%-- Success message display --%>
    <%
        String updateStatus = (String) session.getAttribute("updateStatus");
        if(updateStatus != null && !updateStatus.isEmpty()) {
            out.println("<div id='successMessage' class='alert alert-success' role='alert'>" + updateStatus + "</div>");
            session.removeAttribute("updateStatus"); // Remove attribute so it doesn't show again after refresh
        }
    %>
    <form action="profileServlet" method="POST">
        <div class="row gutters">
            <div class="col-xl-3 col-lg-3 col-md-12 col-sm-12 col-12">
                <div class="card h-100">
                    <div class="card-body">
                        <div class="account-settings">
                            <div class="user-profile">
                                <div class="user-avatar">
                                    <img src="https://bootdey.com/img/Content/avatar/avatar7.png" alt="Maxwell Admin">
                                </div>
                                <h5 class="user-name"><%= pseudo %>
                                </h5>
                            </div>
                            <div class="about">
                                <h5>About</h5>
                                <p>I'm <%=pseudo%>. Full Stack Designer I enjoy creating user-centric, delightful and human
                                    experiences.</p>
                            </div>
                        </div>
                    </div>
                </div>
            </div>
            <div class="col-xl-9 col-lg-9 col-md-12 col-sm-12 col-12">
                <div class="card h-100">
                    <div class="card-body">
                        <div class="row gutters">
                            <div class="col-xl-12 col-lg-12 col-md-12 col-sm-12 col-12">
                                <h6 class="mb-2 text-primary">Personal Details</h6>
                            </div>

                            <!-- Personal Details Fields -->
                            <div class="col-xl-6 col-lg-6 col-md-6 col-sm-6 col-12">
                                <div class="form-group">
                                    <label for="fullName">Pseudo</label>
                                    <input type="text" readonly class="form-control" name="pseudo"  id="fullName">
                                </div>
                            </div>
                            <div class="col-xl-6 col-lg-6 col-md-6 col-sm-6 col-12">
                                <div class="form-group">
                                    <label for="age">Age</label>
                                    <input type="number" name="age" class="form-control" id="age">
                                </div>
                            </div>
                            <div class="col-xl-6 col-lg-6 col-md-6 col-sm-6 col-12">
                                <div class="form-group">
                                    <label for="genre">genre</label>
                                    <input type="text" class="form-control" name="genre" id="genre" >
                                </div>
                            </div>
                            <div class="col-xl-6 col-lg-6 col-md-6 col-sm-6 col-12">
                                <div class="form-group">
                                    <button type="button" id="submit" name="changePassword" class="btn btn-secondary">
                                        Changer Mot de Passe
                                    </button>
                                </div>
                            </div>
                        </div>
                        <div class="row gutters">
                            <div class="col-xl-12 col-lg-12 col-md-12 col-sm-12 col-12">
                                <h6 class="mt-3 mb-2 text-primary">Game Stats</h6>
                            </div>
                            <!-- Address Fields -->
                            <div class="col-xl-6 col-lg-6 col-md-6 col-sm-6 col-12">
                                <div class="form-group">
                                    <label for="nbrPart">Nombre de parties jouées</label>
                                    <input type="name" class="form-control"  readonly
                                           id="nbrPart">
                                </div>
                            </div>
                            <div class="col-xl-6 col-lg-6 col-md-6 col-sm-6 col-12">
                                <div class="form-group">
                                    <label for="nbrVict">Nombre de victoires</label>
                                    <input type="name" class="form-control" readonly
                                           id="nbrVict">
                                </div>
                            </div>
                            <div class="col-xl-6 col-lg-6 col-md-6 col-sm-6 col-12">
                                <div class="form-group">
                                    <label for="moy">Score Moyen</label>
                                    <input type="name" class="form-control"  readonly id="moy">
                                </div>
                            </div>
                            <div class="col-xl-6 col-lg-6 col-md-6 col-sm-6 col-12">
                                <div class="form-group">
                                    <label for="clcReussi">RatioClicRéussi</label>
                                    <input type="name" class="form-control"  readonly
                                           id="clcReussi">
                                </div>
                            </div>
                            <div class="col-xl-6 col-lg-6 col-md-6 col-sm-6 col-12">
                                <div class="form-group">
                                    <label for="clcRapide">RatioClicRapide</label>
                                    <input type="name"  readonly class="form-control"
                                           id="clcRapide">
                                </div>
                            </div>
                        </div>
                        <div class="row gutters">
                            <div class="col-xl-12 col-lg-12 col-md-12 col-sm-12 col-12">
                                <div class="text-right">
                                    <button type="button" id="submit" name="submit" class="btn btn-secondary" onclick="window.history.back();">Cancel</button>

                                    <button type="submit" id="submit" name="submit" class="btn btn-primary">Update
                                    </button>
                                </div>
                            </div>
                        </div>
                    </div>
                </div>
            </div>
        </div>
    </form>
</div>

<script src="https://cdn.jsdelivr.net/npm/bootstrap@4.5.3/dist/js/bootstrap.bundle.min.js"
        integrity="sha384-ho+j7jyWK8fNQe+A12Hb8AhRq26LrZ/JpcUGGOn+Y7RsweNrtN/tE3MoK7ZeZDyx"
        crossorigin="anonymous"></script>
<%-- Vanilla JavaScript to hide the message after 3 seconds --%>
<script>
    // window.onload = function() {
    //     // Redirect to the ProfileServlet with the username as a parameter
    //     // Check if the success message exists
    //     var successMsg = document.getElementById('successMessage');
    //     if (successMsg) {
    //         setTimeout(function() {
    //             var fadeEffect = setInterval(function () {
    //                 if (!successMsg.style.opacity) {
    //                     successMsg.style.opacity = 1;
    //                 }
    //                 if (successMsg.style.opacity > 0) {
    //                     successMsg.style.opacity -= 0.1;
    //                 } else {
    //                     clearInterval(fadeEffect);
    //                     successMsg.style.display = 'none'; // Optionally hide it completely after fading
    //                 }
    //             }, 100);
    //         }, 3000);
    //     }
    // };

    <%--window.onload = function() {--%>
    <%--    var username = '<%= (String) session.getAttribute("pseudo") %>';--%>
    <%--    window.location.href = '/profileServlet?username=' + encodeURIComponent(username);--%>
    <%--};--%>
</script>
</body>
</html>
