package org.matteria.i18n.api;


public interface DictionaryService {
    Translation translate(final Language languageFrom,  final Language languageTo, final String message);
}
