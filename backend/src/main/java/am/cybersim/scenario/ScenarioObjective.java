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

@Entity
@Table(name = "scenario_objectives")
public class ScenarioObjective {

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

    protected ScenarioObjective() {
    }

    public ScenarioObjective(int position, String text) {
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
