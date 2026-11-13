package vn.tsolve.common.config;

import io.swagger.v3.core.converter.AnnotatedType;
import io.swagger.v3.core.converter.ModelConverter;
import io.swagger.v3.core.converter.ModelConverterContext;
import io.swagger.v3.oas.models.media.ComposedSchema;
import io.swagger.v3.oas.models.media.Schema;
import java.util.Iterator;
import vn.tsolve.common.session.dto.CurrentUserResponse;

/**
 * Writes {@code CurrentUser.department} as {@code allOf: [$ref DepartmentRef]} with {@code nullable: true}.
 * swagger-core drops {@code nullable} on a plain {@code $ref} property, so the annotation alone is not enough
 * (AC-003.6). Registered through {@code META-INF/services} for swagger-core's ModelConverters; whether springdoc's
 * {@code /v3/api-docs} applies it is checked by SessionMeIT, not assumed.
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
                    model.getProperties()
                            .put(
                                    "department",
                                    new ComposedSchema()
                                            .addAllOfItem(new Schema<>().$ref("#/components/schemas/DepartmentRef"))
                                            .nullable(true));
                }
            }
        }
        return resolved;
    }
}
