package am.cybersim.scenario;

import am.cybersim.scenario.ScenarioEnums.ResourceType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.util.LinkedHashMap;
import java.util.Map;

/** A simulated cloud resource in the scenario's initial infrastructure state. */
@Entity
@Table(name = "scenario_resources")
public class ScenarioResource {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "scenario_id")
    private Scenario scenario;

    @Column(nullable = false)
    private int position;

    @Column(name = "resource_key", nullable = false)
    private String resourceKey;

    @Enumerated(EnumType.STRING)
    @Column(name = "resource_type", nullable = false)
    private ResourceType resourceType;

    @Column(nullable = false)
    private String name;

    @Column(nullable = false)
    private String region;

    @Column(nullable = false)
    private String status;

    /** Type-specific attributes (IP address, instance size, policy, …) — only displayed, never queried. */
    @JdbcTypeCode(SqlTypes.JSON)
    @Column(nullable = false, columnDefinition = "jsonb")
    private Map<String, Object> properties = new LinkedHashMap<>();

    protected ScenarioResource() {
    }

    public ScenarioResource(int position, String resourceKey, ResourceType resourceType, String name, String region,
                            String status, Map<String, Object> properties) {
        this.position = position;
        this.resourceKey = resourceKey;
        this.resourceType = resourceType;
        this.name = name;
        this.region = region;
        this.status = status;
        this.properties = properties == null ? new LinkedHashMap<>() : new LinkedHashMap<>(properties);
    }

    void attachTo(Scenario scenario) {
        this.scenario = scenario;
    }

    public int getPosition() {
        return position;
    }

    public String getResourceKey() {
        return resourceKey;
    }

    public ResourceType getResourceType() {
        return resourceType;
    }

    public String getName() {
        return name;
    }

    public String getRegion() {
        return region;
    }

    public String getStatus() {
        return status;
    }

    public Map<String, Object> getProperties() {
        return properties;
    }
}
