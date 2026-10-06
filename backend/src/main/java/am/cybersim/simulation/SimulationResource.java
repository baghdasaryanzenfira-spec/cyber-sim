package am.cybersim.simulation;

import am.cybersim.scenario.ScenarioEnums.ResourceType;
import am.cybersim.scenario.ScenarioResource;
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

/** The student's private copy of a simulated cloud resource; its status changes when actions are applied. */
@Entity
@Table(name = "simulation_resources")
public class SimulationResource {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "simulation_id")
    private Simulation simulation;

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

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(nullable = false, columnDefinition = "jsonb")
    private Map<String, Object> properties = new LinkedHashMap<>();

    protected SimulationResource() {
    }

    static SimulationResource copyOf(ScenarioResource template) {
        SimulationResource r = new SimulationResource();
        r.position = template.getPosition();
        r.resourceKey = template.getResourceKey();
        r.resourceType = template.getResourceType();
        r.name = template.getName();
        r.region = template.getRegion();
        r.status = template.getStatus();
        r.properties = new LinkedHashMap<>(template.getProperties());
        return r;
    }

    void attachTo(Simulation simulation) {
        this.simulation = simulation;
    }

    void changeStatus(String status) {
        this.status = status;
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
