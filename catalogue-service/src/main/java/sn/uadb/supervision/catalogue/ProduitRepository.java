package sn.uadb.supervision.catalogue;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;

public interface ProduitRepository extends JpaRepository<Produit, Long> {

    long countByStock(int stock);

    /** Décrément conditionnel en une requête : deux instances ne peuvent pas survendre. */
    @Modifying(clearAutomatically = true)
    @Query("update Produit p set p.stock = p.stock - :quantite where p.id = :id and p.stock >= :quantite")
    int reserver(Long id, int quantite);

    @Modifying(clearAutomatically = true)
    @Query("update Produit p set p.stock = p.stock + :quantite where p.id = :id")
    int reapprovisionner(Long id, int quantite);
}
