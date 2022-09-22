package org.matteria.i18n;

import java.lang.annotation.Annotation;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.Arrays;
import java.util.HashMap;
import java.util.Locale;
import java.util.regex.Pattern;
import javax.servlet.http.HttpServletRequest;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.aspectj.lang.reflect.MethodSignature;
import org.matteria.i18n.annotation.I18n;
import org.matteria.i18n.annotation.Translatable;
import org.matteria.i18n.annotation.TranslatableRegexArgument;
import org.matteria.i18n.annotation.TranslatableStringArgument;
import org.matteria.i18n.api.DictionaryService;
import org.matteria.i18n.api.Language;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.ResponseEntity;

@Aspect
public class I18nProxy {
    private final HttpServletRequest httpServletRequest;
    private final Language nativeLanguage;
    private final DictionaryService dictionaryService;

    public I18nProxy(final HttpServletRequest httpServletRequest,
                     final DictionaryService dictionaryService,
                     @Value("${i18n.language.native}") final String nativeLanguage) {
        if (nativeLanguage == null) {
            throw new RuntimeException("Language is not declared");
        }
        this.httpServletRequest = httpServletRequest;
        this.dictionaryService = dictionaryService;
        this.nativeLanguage = new Language(nativeLanguage);
    }

    @Around(value = "@annotation(i18n)")
    public Object translate(final ProceedingJoinPoint joinPoint, final I18n i18n) throws Throwable {
        /* Before */
        final var httpServletRequestLocale = this.httpServletRequest.getLocale().getLanguage();
        if (httpServletRequestLocale.equals(nativeLanguage.title())) {
            return joinPoint.proceed();
        }
        final var methodSignature = (MethodSignature) joinPoint.getSignature();
        final var parameters = methodSignature.getMethod().getParameters();
        final Object[] targets = joinPoint.getArgs();
        // Prepare the variable for the future translations instead of original object
        final var translatedObjects = new Object[targets.length];
        for (int i = 0; i < targets.length; i++) {
            final Object target = targets[i];
            final var parameter = parameters[i];
            for (final Annotation declaredAnnotation : parameter.getDeclaredAnnotations()) {
                if (declaredAnnotation instanceof final TranslatableRegexArgument translatableRegexArgument) {
                    var targetString = (String) target;
                    final var regex = translatableRegexArgument.regex();
                    final var targetGroups = translatableRegexArgument.targetGroups();
                    final var delimiter = translatableRegexArgument.delimiter();
                    final var pattern = Pattern.compile(regex + delimiter);
                    final var matcher = pattern.matcher(targetString + delimiter);
                    while (matcher.find()) {
                        for (final int targetGroup : targetGroups) {
                            final var toTranslate = matcher.group(targetGroup);
                            final var translation = dictionaryService
                                    .translate(new Language(httpServletRequestLocale), nativeLanguage, toTranslate)
                                    .nativeKey();
                            if (translation != null) {
                                targetString = targetString.replace(toTranslate, translation);
                            }
                        }
                    }
                    translatedObjects[i] = targetString;
                } else if (declaredAnnotation instanceof TranslatableStringArgument) {
                    final var targetObject = (String) target;
                    final var translation = dictionaryService.translate(new Language(httpServletRequestLocale), nativeLanguage, targetObject).nativeKey();
                    translatedObjects[i] = translation;
                }
            }
        }
        for (int i = 0; i < translatedObjects.length; i++) {
            if (translatedObjects[i] == null) {
                translatedObjects[i] = joinPoint.getArgs()[i];
            }
        }
        /* Before - End */
        final var proceeded = joinPoint.proceed(translatedObjects);
        /* After */
        final var responseEntity = (ResponseEntity<?>) proceeded;
        final var body = responseEntity.getBody();
        final var map = new HashMap<String, Object>();
        if (body != null) {
            if (body.getClass().isInterface() || body.getClass().isAnonymousClass()) {
                for (final Method declaredMethod : body.getClass().getDeclaredMethods()) {
                    var name = "";
                    declaredMethod.setAccessible(true);
                    final var operated = declaredMethod.invoke(body);
                    declaredMethod.setAccessible(false);

                    if (declaredMethod.getName().startsWith("get")) {
                        name = declaredMethod.getName().substring(3).toLowerCase(Locale.ROOT);
                    } else {
                        name = declaredMethod.getName().toLowerCase(Locale.ROOT);
                    }
                    if (Arrays.stream(declaredMethod.getDeclaredAnnotations()).noneMatch((annotation) -> annotation instanceof Translatable)) {
                        map.put(name, operated);
                    } else if (operated instanceof final String s) {
                        final var translated = dictionaryService.translate(nativeLanguage, new Language(httpServletRequestLocale), s);
                        map.put(name, translated.foreignValue());
                    }
                }
            } else if (body.getClass().isMemberClass()) {
                for (final Field declaredField : body.getClass().getDeclaredFields()) {
                    declaredField.setAccessible(true);
                    final var object = declaredField.get(body);
                    if (object instanceof final String message) {
                        final var translation = dictionaryService.translate(nativeLanguage, new Language(httpServletRequestLocale), message);
                        map.put(declaredField.getName(), translation.foreignValue());
                    }
                }
            }
            return ResponseEntity.status(responseEntity.getStatusCode()).body(map);
        }
        /* After - ends*/
        return proceeded;
    }
}
