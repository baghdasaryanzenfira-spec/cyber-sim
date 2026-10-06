package am.cybersim.scenario;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;

/** Static hint authored with the scenario; used by the mock AI provider and as a fallback. */
@Entity
@Table(name = "scenario_hints")
public class ScenarioHint {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "scenario_id")
    private Scenario scenario;

    @Column(nullable = false)
    private int position;

    @Column(nullable = false)
    private String text;

    protected ScenarioHint() {
    }

    public ScenarioHint(int position, String text) {
        this.position = position;
        this.text = text;
    }

    void attachTo(Scenario scenario) {
        this.scenario = scenario;
    }

    public int getPosition() {
        return position;
    }

    public String getText() {
        return text;
    }
}
