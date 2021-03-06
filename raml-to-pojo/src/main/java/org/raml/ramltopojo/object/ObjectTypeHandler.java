package org.raml.ramltopojo.object;

import com.google.common.base.Optional;
import com.squareup.javapoet.*;
import org.raml.ramltopojo.*;
import org.raml.ramltopojo.extensions.ObjectPluginContext;
import org.raml.ramltopojo.extensions.ObjectPluginContextImpl;
import org.raml.ramltopojo.extensions.ObjectTypeHandlerPlugin;
import org.raml.v2.api.model.v10.datamodel.ObjectTypeDeclaration;
import org.raml.v2.api.model.v10.datamodel.TypeDeclaration;

import javax.lang.model.element.Modifier;
import java.util.ArrayList;

/**
 * Created. There, you have it.
 */
public class ObjectTypeHandler implements TypeHandler {

    private final String name;
    private final ObjectTypeDeclaration objectTypeDeclaration;

    public ObjectTypeHandler(String name, ObjectTypeDeclaration objectTypeDeclaration) {
        this.name = name;
        this.objectTypeDeclaration = objectTypeDeclaration;
    }


    @Override
    public ClassName javaClassName(GenerationContext generationContext, EventType type) {

        ObjectPluginContext context = new ObjectPluginContextImpl(generationContext, null);


        ObjectTypeHandlerPlugin plugin = generationContext.pluginsForObjects(Utils.allParents(objectTypeDeclaration, new ArrayList<TypeDeclaration>()).toArray(new TypeDeclaration[0]));
        ClassName className;
        if ( type == EventType.IMPLEMENTATION ) {
            className = generationContext.buildDefaultClassName(Names.typeName(name, "Impl"), EventType.IMPLEMENTATION);
        } else {

            className = generationContext.buildDefaultClassName(Names.typeName(name), EventType.INTERFACE);
        }

        return plugin.className(context, objectTypeDeclaration, className, type);
    }

    @Override
    public TypeName javaClassReference(GenerationContext generationContext, EventType type) {
        return javaClassName(generationContext, type);
    }

    @Override
    // TODO deal with null interface spec.
    public Optional<CreationResult> create(GenerationContext generationContext, CreationResult result) {

        // I need to createHandler an interface and an implementation.
        ObjectPluginContext context = new ObjectPluginContextImpl(generationContext, result);
        TypeSpec interfaceSpec = createInterface(context,  result, generationContext);
        TypeSpec implementationSpec = createImplementation(context,  result, generationContext);

        if ( interfaceSpec == null ) {

            return Optional.absent();
        } else {
            return Optional.of(result.withInterface(interfaceSpec).withImplementation(implementationSpec));
        }
    }

    private TypeSpec createImplementation(ObjectPluginContext objectPluginContext, CreationResult result, GenerationContext generationContext) {

        ClassName className = result.getJavaName(EventType.IMPLEMENTATION);
        TypeSpec.Builder typeSpec = TypeSpec
                .classBuilder(className)
                .addSuperinterface(result.getJavaName(EventType.INTERFACE))
                .addModifiers(Modifier.PUBLIC);

        Optional<String> discriminator = Optional.fromNullable(objectTypeDeclaration.discriminator());

        for (TypeDeclaration propertyDeclaration : objectTypeDeclaration.properties()) {

            TypeName tn;
            if ( TypeDeclarationType.isNewInlineType(propertyDeclaration) ){

                CreationResult cr = result.internalType(propertyDeclaration.name());
                tn = cr.getJavaName(EventType.INTERFACE);

            }  else {

                tn = findType(propertyDeclaration.type(), propertyDeclaration, generationContext, EventType.INTERFACE);
            }

            FieldSpec.Builder field = FieldSpec.builder(tn, Names.variableName(propertyDeclaration.name())).addModifiers(Modifier.PRIVATE);
            if ( propertyDeclaration.name().equals(discriminator.orNull())) {

                String discriminatorValue = Optional.fromNullable(objectTypeDeclaration.discriminatorValue()).or(objectTypeDeclaration.name());
                field.addModifiers(Modifier.PRIVATE, Modifier.FINAL)
                        .initializer(CodeBlock.builder().add("$S", discriminatorValue).build());

            }
            field = generationContext.pluginsForObjects(objectTypeDeclaration, propertyDeclaration).fieldBuilt(objectPluginContext, propertyDeclaration, field, EventType.IMPLEMENTATION);
            if ( field != null ) {
                typeSpec.addField(field.build());
            }

            MethodSpec.Builder getMethod = MethodSpec.methodBuilder(Names.methodName("get", propertyDeclaration.name()))
                    .addModifiers(Modifier.PUBLIC)
                    .addCode(CodeBlock.builder().addStatement("return this." + Names.variableName(propertyDeclaration.name())).build())
                    .returns(tn);
            getMethod = generationContext.pluginsForObjects(objectTypeDeclaration, propertyDeclaration).getterBuilt(objectPluginContext, propertyDeclaration, getMethod, EventType.IMPLEMENTATION);
           if ( getMethod != null ) {
               typeSpec.addMethod(getMethod.build());
           }

            if ( propertyDeclaration.name().equals(discriminator.orNull())) {

                continue;
            }

            MethodSpec.Builder setMethod = MethodSpec.methodBuilder(Names.methodName("set", propertyDeclaration.name()))
                    .addModifiers(Modifier.PUBLIC)
                    .addCode(CodeBlock.builder().addStatement("this." + Names.variableName(propertyDeclaration.name()) + " = " + Names.variableName(propertyDeclaration.name())).build())
                    .addParameter(tn, Names.variableName(propertyDeclaration.name()));
            setMethod = generationContext.pluginsForObjects(objectTypeDeclaration, propertyDeclaration).setterBuilt(objectPluginContext, propertyDeclaration, setMethod, EventType.IMPLEMENTATION);
            if ( setMethod != null ) {
                typeSpec.addMethod(setMethod.build());
            }
        }

        typeSpec = generationContext.pluginsForObjects(objectTypeDeclaration).classCreated(objectPluginContext, objectTypeDeclaration, typeSpec, EventType.IMPLEMENTATION);
        if ( typeSpec == null ) {
            return null;
        }

        return typeSpec.build();
    }

    private TypeSpec createInterface(ObjectPluginContext objectPluginContext, CreationResult result, GenerationContext generationContext) {

        ClassName interf = result.getJavaName(EventType.INTERFACE);
        TypeSpec.Builder typeSpec = TypeSpec
                .interfaceBuilder(interf)
                .addModifiers(Modifier.PUBLIC, Modifier.STATIC);
        typeSpec = generationContext.pluginsForObjects(objectTypeDeclaration).classCreated(objectPluginContext, objectTypeDeclaration, typeSpec, EventType.INTERFACE);
        if ( typeSpec == null ) {
            return null;
        }

        Optional<String> discriminator = Optional.fromNullable(objectTypeDeclaration.discriminator());

        for (TypeDeclaration typeDeclaration : objectTypeDeclaration.parentTypes()) {

            if (typeDeclaration instanceof ObjectTypeDeclaration) {

                if (typeDeclaration.name().equals("object")) {
                    continue;
                }

                TypeName inherits = findType(typeDeclaration.name(), typeDeclaration, generationContext, EventType.INTERFACE);
                typeSpec.addSuperinterface(inherits);
            } else {

                throw new GenerationException("ramltopojo does not support inheriting from "
                        + Utils.declarationType(typeDeclaration) + " name: " + typeDeclaration.name() + " and " + typeDeclaration.type());
            }
        }

        for (TypeDeclaration propertyDeclaration : objectTypeDeclaration.properties()) {


            TypeName tn = null;
            if ( TypeDeclarationType.isNewInlineType(propertyDeclaration) ){

                Optional<CreationResult> cr = TypeDeclarationType.createInlineType(interf, result.getJavaName(EventType.IMPLEMENTATION),  Names.typeName(propertyDeclaration.name(), "type"), propertyDeclaration, generationContext);
                if ( cr.isPresent() ) {
                    result.withInternalType(propertyDeclaration.name(), cr.get());
                    tn = cr.get().getJavaName(EventType.INTERFACE);
                }
            }  else {

                tn = findType(propertyDeclaration.type(), propertyDeclaration, generationContext, EventType.INTERFACE);
            }

            if (tn == null) {
                continue;
            }
            MethodSpec.Builder getMethod = MethodSpec.methodBuilder(Names.methodName("get", propertyDeclaration.name()))
                    .addModifiers(Modifier.PUBLIC, Modifier.ABSTRACT)
                    .returns(tn);
            getMethod = generationContext.pluginsForObjects(objectTypeDeclaration, propertyDeclaration).getterBuilt(objectPluginContext, propertyDeclaration, getMethod, EventType.INTERFACE);

            if ( getMethod != null ) {
                typeSpec.addMethod(getMethod.build());

                if (propertyDeclaration.name().equals(discriminator.orNull())) {

                    continue;
                }
            }

            MethodSpec.Builder setMethod = MethodSpec.methodBuilder(Names.methodName("set", propertyDeclaration.name()))
                    .addModifiers(Modifier.PUBLIC, Modifier.ABSTRACT)
                    .addParameter(tn, Names.variableName(propertyDeclaration.name()));
            setMethod = generationContext.pluginsForObjects(objectTypeDeclaration, propertyDeclaration).setterBuilt(objectPluginContext, propertyDeclaration, setMethod, EventType.INTERFACE);
            if ( setMethod != null ) {
                typeSpec.addMethod(setMethod.build());
            }

        }

        return typeSpec.build();
    }

    private TypeName findType(String typeName, TypeDeclaration type, GenerationContext generationContext, EventType eventType) {

        return TypeDeclarationType.calculateTypeName(typeName, type, generationContext,eventType );
    }
}
