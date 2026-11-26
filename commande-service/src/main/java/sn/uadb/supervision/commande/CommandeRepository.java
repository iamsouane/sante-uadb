package sn.uadb.supervision.commande;

import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface CommandeRepository extends JpaRepository<Commande, Long> {

    List<Commande> findTop50ByOrderByIdDesc();
}
