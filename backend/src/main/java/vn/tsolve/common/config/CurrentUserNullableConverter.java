package vn.tsolve.common.config;

import io.swagger.v3.core.converter.AnnotatedType;
import io.swagger.v3.core.converter.ModelConverter;
import io.swagger.v3.core.converter.ModelConverterContext;
import io.swagger.v3.oas.models.media.Schema;
import java.util.Iterator;
import vn.tsolve.common.session.dto.CurrentUserResponse;

/**
 * Marks {@code CurrentUser.department} as nullable in the generated schema. swagger-core drops {@code nullable} on a
 * {@code $ref} property, so the annotation alone is not enough (AC-003.6). Loaded by swagger-core through
 * {@code META-INF/services}, so it also applies to springdoc's {@code /v3/api-docs}.
 */
public class CurrentUserNullableConverter implements ModelConverter {
    @Override
    @SuppressWarnings("rawtypes")
    public Schema resolve(AnnotatedType type, ModelConverterContext context, Iterator<ModelConverter> chain) {
        Schema resolved = chain.hasNext() ? chain.next().resolve(type, context, chain) : null;
        if (type.getType() instanceof Class<?> clazz && clazz == CurrentUserResponse.class) {
            Schema model = context.getDefinedModels().get("CurrentUser");
            if (model != null && model.getProperties() != null) {
                Schema department = (Schema) model.getProperties().get("department");
                if (department != null) {
                    department.setNullable(true);
                }
            }
        }
        return resolved;
    }
}
