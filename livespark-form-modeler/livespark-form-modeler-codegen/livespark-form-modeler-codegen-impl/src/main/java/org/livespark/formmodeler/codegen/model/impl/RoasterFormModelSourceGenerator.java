package org.livespark.formmodeler.codegen.model.impl;

import static org.livespark.formmodeler.codegen.util.SourceGenerationUtil.ERRAI_BINDABLE;
import static org.livespark.formmodeler.codegen.util.SourceGenerationUtil.ERRAI_PORTABLE;
import static org.livespark.formmodeler.codegen.util.SourceGenerationUtil.FORM_MODEL_CLASS;
import static org.livespark.formmodeler.codegen.util.SourceGenerationUtil.INJECT_NAMED;
import static org.livespark.formmodeler.codegen.util.SourceGenerationUtil.VALIDATION_NOT_NULL;
import static org.livespark.formmodeler.codegen.util.SourceGenerationUtil.VALIDATION_VALID;

import java.util.Arrays;
import java.util.Iterator;
import java.util.List;

import javax.enterprise.context.ApplicationScoped;
import javax.inject.Inject;

import org.apache.commons.lang3.ArrayUtils;
import org.jboss.forge.roaster.Roaster;
import org.jboss.forge.roaster.model.source.FieldSource;
import org.jboss.forge.roaster.model.source.JavaClassSource;
import org.jboss.forge.roaster.model.source.MethodSource;
import org.livespark.formmodeler.codegen.SourceGenerationContext;
import org.livespark.formmodeler.codegen.model.FormModelSourceGenerator;
import org.livespark.formmodeler.model.DataHolder;
import org.livespark.formmodeler.service.FieldManager;

/**
 * Created by pefernan on 4/27/15.
 */
@ApplicationScoped
public class RoasterFormModelSourceGenerator implements FormModelSourceGenerator {

    @Inject
    private ConstructorGenerator constructorGenerator;

    @Override
    public String generateFormModelSource( SourceGenerationContext context ) {

        JavaClassSource modelClass = Roaster.create( JavaClassSource.class );

        addTypeSignature( context, modelClass );
        addImports( context, modelClass );
        addTypeAnnotations( context, modelClass );
        addProperties( context, modelClass );
        addConstructors( context, modelClass );
        addMethodImpls( context, modelClass );

        return modelClass.toString();
    }

    private void addImports( SourceGenerationContext context,
                             JavaClassSource modelClass ) {
        modelClass.addImport( List.class );
        modelClass.addImport( Arrays.class );
    }

    private void addMethodImpls( SourceGenerationContext context,
                                 JavaClassSource modelClass ) {
        addGetDataModelsImpl( context, modelClass );
    }

    private void addGetDataModelsImpl( SourceGenerationContext context,
                                       JavaClassSource modelClass ) {
        MethodSource<JavaClassSource> getDataModels = modelClass.addMethod();
        getDataModels.setPublic()
                     .setName( "getDataModels" )
                     .setReturnType( "List<Object>" )
                     .setBody( generateGetDataModelsBody( context ) )
                     .addAnnotation( Override.class );
    }

    private String generateGetDataModelsBody( SourceGenerationContext context ) {
        StringBuilder body = new StringBuilder();

        body.append( "return Arrays.<Object>asList(" );

        Iterator<DataHolder> iter = context.getFormDefinition().getDataHolders().iterator();
        while ( iter.hasNext() ) {
            DataHolder holder = iter.next();
            body.append( holder.getName() );
            if ( iter.hasNext() ) {
                body.append( ", " );
            }
        }
        body.append( ");" );

        return body.toString();
    }

    private void addConstructors( SourceGenerationContext context,
                            JavaClassSource modelClass ) {
        constructorGenerator.addNoArgConstructor( modelClass );
        constructorGenerator.addFormModelConstructor( context, modelClass );
    }

    private void addTypeAnnotations( SourceGenerationContext context,
                            JavaClassSource modelClass ) {
        modelClass.addAnnotation( ERRAI_PORTABLE );
        modelClass.addAnnotation( ERRAI_BINDABLE );
        modelClass.addAnnotation( INJECT_NAMED ).setStringValue( context.getModelName() );
    }

    private void addTypeSignature( SourceGenerationContext context,
                            JavaClassSource modelClass ) {
        modelClass.setPackage( context.getSharedPackage().getPackageName() )
                .setPublic()
                .setName( context.getModelName() );

        modelClass.setSuperType( FORM_MODEL_CLASS );
    }

    private void addProperties( SourceGenerationContext context, JavaClassSource modelClass ) {
        for ( DataHolder dataHolder : context.getFormDefinition().getDataHolders() ) {
            FieldSource<JavaClassSource> modelField = modelClass.addProperty( dataHolder.getType(),
                                                                              dataHolder.getName() ).getField();

            if ( ArrayUtils.contains( FieldManager.BASIC_TYPES,
                                      dataHolder.getType() ) ) {
                modelField.addAnnotation( VALIDATION_NOT_NULL );
            } else {
                modelField.addAnnotation( VALIDATION_VALID );
            }

        }
    }
}
