package sn.uadb.supervision.commun;

import org.springframework.boot.context.properties.ConfigurationProperties;

/** Comptes techniques ; les mots de passe viennent de l'environnement, jamais du dépôt. */
@ConfigurationProperties("supervision.securite")
public record SecuriteProperties(String utilisateur, String motDePasse, String admin, String motDePasseAdmin) {
}
