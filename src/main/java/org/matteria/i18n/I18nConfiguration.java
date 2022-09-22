package org.matteria.i18n;

import javax.servlet.http.HttpServletRequest;
import org.matteria.i18n.api.DictionaryService;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class I18nConfiguration {
    @Bean
    public I18nProxy translatableProxy(final HttpServletRequest httpServletRequest,
                                       final DictionaryService dictionaryService,
                                       @Value("${i18n.language.native}") final String nativeLanguage) {
        return new I18nProxy(httpServletRequest, dictionaryService, nativeLanguage);
    }

}
