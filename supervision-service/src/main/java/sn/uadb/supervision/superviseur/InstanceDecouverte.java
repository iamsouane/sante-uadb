package sn.uadb.supervision.superviseur;

/** Instance telle que déclarée dans Eureka. */
public record InstanceDecouverte(String service, String id, String urlBase, String urlSante, String statutEureka) {
}
