package org.raml.ramltopojo.object;

import com.google.common.base.Predicate;
import com.google.common.collect.FluentIterable;
import com.squareup.javapoet.ClassName;
import org.junit.Test;
import org.raml.ramltopojo.CreationResult;
import org.raml.v2.api.model.v10.api.Api;
import org.raml.v2.api.model.v10.datamodel.ObjectTypeDeclaration;
import org.raml.v2.api.model.v10.datamodel.TypeDeclaration;

import javax.annotation.Nullable;
import java.util.List;

import static org.hamcrest.Matchers.*;
import static org.hamcrest.core.IsEqual.equalTo;
import static org.junit.Assert.assertThat;
import static org.raml.testutils.matchers.FieldSpecMatchers.fieldName;
import static org.raml.testutils.matchers.FieldSpecMatchers.fieldType;
import static org.raml.testutils.matchers.MethodSpecMatchers.*;
import static org.raml.testutils.matchers.ParameterSpecMatchers.type;
import static org.raml.testutils.matchers.TypeSpecMatchers.*;

/**
 * Created. There, you have it.
 */
public class ObjectTypeHandlerTest {
    @Test
    public void create() throws Exception {

        Api api = RamlLoader.load(this.getClass().getResourceAsStream("simplest-type.raml"));
        ObjectTypeHandler handler = new ObjectTypeHandler(findTypes("foo", api.types()));

        CreationResult r = handler.create();

        assertThat(r.getInterface(), is(allOf(
                name(equalTo("Foo")),
                methods(contains(
                        allOf(methodName(equalTo("getName")), returnType(equalTo(ClassName.get(String.class)))),
                        allOf(methodName(equalTo("setName")), parameters(contains(type(equalTo(ClassName.get(String.class)))))),
                        allOf(methodName(equalTo("getAge")), returnType(equalTo(ClassName.INT))),
                        allOf(methodName(equalTo("setAge")), parameters(contains(type(equalTo(ClassName.INT)))))
                ))
        )));

        assertThat(r.getImplementation().get(), is(allOf(
                name(equalTo("FooImpl")),
                fields(contains(
                   allOf(fieldName(equalTo("name")), fieldType(equalTo(ClassName.get(String.class)))),
                   allOf(fieldName(equalTo("age")), fieldType(equalTo(ClassName.INT)))
                )),
                methods(contains(
                        allOf(methodName(equalTo("getName")), returnType(equalTo(ClassName.get(String.class)))),
                        allOf(methodName(equalTo("setName")), parameters(contains(type(equalTo(ClassName.get(String.class)))))),
                        allOf(methodName(equalTo("getAge")), returnType(equalTo(ClassName.INT))),
                        allOf(methodName(equalTo("setAge")), parameters(contains(type(equalTo(ClassName.INT)))))
                ))
        )));

        System.err.println(r.getInterface().toString());
        System.err.println(r.getImplementation().toString());
    }

    private ObjectTypeDeclaration findTypes(final String name, List<TypeDeclaration> types) {
        return (ObjectTypeDeclaration) FluentIterable.from(types).firstMatch(new Predicate<TypeDeclaration>() {
            @Override
            public boolean apply(@Nullable TypeDeclaration input) {
                return input.name().equals(name);
            }
        }).get();
    }


}