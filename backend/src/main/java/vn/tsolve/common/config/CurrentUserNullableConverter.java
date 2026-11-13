package vn.tsolve.common.config;

import com.fasterxml.jackson.databind.JavaType;
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
 * (AC-003.6). Registered through {@code META-INF/services} for swagger-core's ModelConverters. It matches the type
 * as a {@code Class} (swagger-core, checked by CurrentUserSchemaTest) or a Jackson {@code JavaType} (springdoc, which
 * unwraps {@code ResponseEntity}); SessionMeIT checks the {@code /v3/api-docs} document the full application serves.
 */
public class CurrentUserNullableConverter implements ModelConverter {
    @Override
    @SuppressWarnings("rawtypes")
    public Schema resolve(AnnotatedType type, ModelConverterContext context, Iterator<ModelConverter> chain) {
        Schema resolved = chain.hasNext() ? chain.next().resolve(type, context, chain) : null;
        if (rawClass(type) == CurrentUserResponse.class) {
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

    // swagger-core passes a Class; springdoc unwraps ResponseEntity and passes a Jackson JavaType.
    private static Class<?> rawClass(AnnotatedType type) {
        if (type.getType() instanceof JavaType javaType) {
            return javaType.getRawClass();
        }
        return type.getType() instanceof Class<?> clazz ? clazz : null;
    }
}
