-- On-demand AI translation is a new AiTask, so the interaction log has to accept it.
ALTER TABLE ai_interactions
    DROP CONSTRAINT ck_ai_type;
ALTER TABLE ai_interactions
    ADD CONSTRAINT ck_ai_type CHECK (interaction_type IN
        ('HINT', 'QUESTION', 'FEEDBACK', 'RECOMMENDATION', 'VARIATION', 'TRANSLATION'));
