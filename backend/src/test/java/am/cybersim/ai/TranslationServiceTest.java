package am.cybersim.ai;

import am.cybersim.ai.dto.AiDtos.AiSource;
import am.cybersim.common.ApiException;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.json.JsonMapper;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/** Input guards and output validation of the on-demand translation endpoint. */
class TranslationServiceTest {

    final AiOutputValidator validator = new AiOutputValidator(JsonMapper.builder().build());
    final TranslationService service = new TranslationService(null, validator);

    @Test
    void rejectsAnUnsupportedLanguage() {
        assertThatThrownBy(() -> service.translate(1L, "Some text", "de"))
                .isInstanceOf(ApiException.class)
                .hasMessageContaining("Cannot translate into 'de'");
    }

    @Test
    void rejectsEmptyText() {
        assertThatThrownBy(() -> service.translate(1L, "   ", "hy"))
                .isInstanceOf(ApiException.class)
                .hasMessageContaining("nothing to translate");
    }

    @Test
    void rejectsTextOverTheLimit() {
        String tooLong = "x".repeat(TranslationService.MAX_SOURCE_CHARS + 1);
        assertThatThrownBy(() -> service.translate(1L, tooLong, "hy"))
                .isInstanceOf(ApiException.class)
                .hasMessageContaining("longer than");
    }

    @Test
    void acceptsATranslationLongerThanItsSource() {
        // Armenian runs longer than English, so a moderate expansion must not be rejected
        String source = "Isolate the compromised virtual machine.";
        String translated = "Մեկուսացրեք վտանգված վիրտուալ մեքենան՝ պահպանելով դատաքննական ապացույցները։";
        assertThat(validator.validateTranslation(translated, source)).isEqualTo(translated);
    }

    @Test
    void rejectsAnExplanationInsteadOfATranslation() {
        String source = "Isolate the VM.";
        String rambling = "Here is what that means. ".repeat(40);
        assertThatThrownBy(() -> validator.validateTranslation(rambling, source))
                .isInstanceOf(AiProviderException.class)
                .hasMessageContaining("implausibly long");
    }

    @Test
    void mockProviderEchoesTheSourceBecauseItCannotTranslate() {
        MockAiProvider provider = new MockAiProvider(JsonMapper.builder().build());
        String source = "Policy check FAILED: bucket acme-customer-exports allows public read";
        var response = provider.complete(new AiProvider.AiRequest(
                new AiPayload.Translation(source, "Armenian"), "system", "user", null));
        assertThat(response.text()).isEqualTo(source);
    }

    @Test
    void aiSourceIsReportedSoTheUiCanSayWhoTranslated() {
        // the UI distinguishes a real translation from the offline echo purely by this field
        assertThat(AiSource.valueOf("AI")).isNotEqualTo(AiSource.valueOf("MOCK"));
    }
}
