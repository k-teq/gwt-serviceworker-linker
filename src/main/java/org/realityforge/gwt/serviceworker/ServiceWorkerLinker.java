package org.realityforge.gwt.serviceworker;

import com.google.gwt.core.ext.LinkerContext;
import com.google.gwt.core.ext.TreeLogger;
import com.google.gwt.core.ext.TreeLogger.Type;
import com.google.gwt.core.ext.UnableToCompleteException;
import com.google.gwt.core.ext.linker.AbstractLinker;
import com.google.gwt.core.ext.linker.ArtifactSet;
import com.google.gwt.core.ext.linker.ConfigurationProperty;
import com.google.gwt.core.ext.linker.EmittedArtifact;
import com.google.gwt.core.ext.linker.EmittedArtifact.Visibility;
import com.google.gwt.core.ext.linker.LinkerOrder;
import com.google.gwt.core.ext.linker.SelectionProperty;
import com.google.gwt.core.ext.linker.Shardable;
import com.google.gwt.core.ext.linker.impl.SelectionInformation;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import javax.annotation.Nonnull;
import javax.annotation.Nullable;

@LinkerOrder( LinkerOrder.Order.POST )
@Shardable
public final class ServiceWorkerLinker
  extends AbstractLinker
{
  @Override
  public String getDescription()
  {
    return "ServiceWorkerLinker";
  }

  @Override
  public ArtifactSet link( @Nonnull final TreeLogger logger,
                           @Nonnull final LinkerContext context,
                           @Nonnull final ArtifactSet artifacts,
                           final boolean onePermutation )
    throws UnableToCompleteException
  {
    if ( onePermutation )
    {
      return perPermutationLink( logger, context, artifacts );
    }
    else
    {
      return perCompileLink( logger, context, artifacts );
    }
  }

  @Nonnull
  private ArtifactSet perCompileLink( @Nonnull final TreeLogger logger,
                                      @Nonnull final LinkerContext context,
                                      @Nonnull final ArtifactSet artifacts )
    throws UnableToCompleteException
  {
    final List<PermutationArtifact> permutationArtifacts =
      new ArrayList<>( artifacts.find( PermutationArtifact.class ) );
    if ( 0 == permutationArtifacts.size() )
    {
      // hosted mode
      return new ArtifactSet( artifacts );
    }

    final Set<String> allPermutationFiles = getAllPermutationFiles( permutationArtifacts );

    // get all artifacts
    final Set<String> allArtifacts = getArtifactsForCompilation( context, artifacts );

    final ArtifactSet results = new ArtifactSet( artifacts );
    for ( final PermutationArtifact permutation : permutationArtifacts )
    {
      // make a copy of all artifacts
      final HashSet<String> filesForCurrentPermutation = new HashSet<>( allArtifacts );
      // remove all permutations
      filesForCurrentPermutation.removeAll( allPermutationFiles );
      // add files of the one permutation we are interested in
      // leaving the common stuff for all permutations in...
      for ( final String file : permutation.getPermutation().getPermutationFiles() )
      {
        if ( allArtifacts.contains( file ) )
        {
          filesForCurrentPermutation.add( file );
        }
      }

      // build manifest
      final Collection<String> externalFiles = getConfiguredStaticFiles( context );
      final String maniFest = writeManifest( logger, externalFiles, filesForCurrentPermutation );
      final String filename =
        permutation.getPermutation().getPermutationName() + Permutation.PERMUTATION_MANIFEST_FILE_ENDING;
      results.add( emitString( logger, maniFest, filename ) );
    }

    results.add( createPermutationMap( logger, permutationArtifacts ) );
    return results;
  }

  @Nonnull
  ArtifactSet perPermutationLink( @Nonnull final TreeLogger logger,
                                  @Nonnull final LinkerContext context,
                                  @Nonnull final ArtifactSet artifacts )
    throws UnableToCompleteException
  {
    final Permutation permutation = calculatePermutation( logger, context, artifacts );
    if ( null == permutation )
    {
      logger.log( Type.ERROR, "Unable to calculate permutation " );
      throw new UnableToCompleteException();
    }

    final ArtifactSet results = new ArtifactSet( artifacts );
    results.add( new PermutationArtifact( ServiceWorkerLinker.class, permutation ) );
    return results;
  }

  @Nonnull
  Set<String> getAllPermutationFiles( @Nonnull final List<PermutationArtifact> artifacts )
  {
    final Set<String> files = new HashSet<>();
    for ( final PermutationArtifact artifact : artifacts )
    {
      files.addAll( artifact.getPermutation().getPermutationFiles() );
    }
    return files;
  }

  @Nonnull
  Set<String> getArtifactsForCompilation( @Nonnull final LinkerContext context,
                                          @Nonnull final ArtifactSet artifacts )
  {
    final Set<String> artifactNames = new HashSet<>();
    for ( final EmittedArtifact artifact : artifacts.find( EmittedArtifact.class ) )
    {
      if ( Visibility.Public == artifact.getVisibility() && shouldAddToManifest( artifact.getPartialPath() ) )
      {
        artifactNames.add( context.getModuleName() + "/" + artifact.getPartialPath() );
      }
    }
    return artifactNames;
  }

  private boolean shouldAddToManifest( @Nonnull final String path )
  {
    return !( path.equals( "compilation-mappings.txt" ) || path.endsWith( ".devmode.js" ) );
  }

  /**
   * Write a manifest file for the given set of artifacts and return it as a
   * string
   *
   * @param staticResources - the static resources of the app, such as index.html file
   * @param cacheResources  the gwt output artifacts like cache.html files
   * @return the manifest as a string
   */
  @Nonnull
  String writeManifest( @Nonnull final TreeLogger logger,
                        @Nonnull final Collection<String> staticResources,
                        @Nonnull final Set<String> cacheResources )
    throws UnableToCompleteException
  {
    final ManifestDescriptor descriptor = new ManifestDescriptor();
    final List<String> cachedResources =
      Stream
        .concat( staticResources.stream(), cacheResources.stream() )
        .sorted()
        .distinct()
        .collect( Collectors.toList() );
    descriptor.getCachedResources().addAll( cachedResources );
    descriptor.getNetworkResources().add( "*" );
    try
    {
      return descriptor.toString();
    }
    catch ( final Exception e )
    {
      logger.log( Type.ERROR, "Error generating manifest: " + e, e );
      throw new UnableToCompleteException();
    }
  }

  @Nonnull
  Collection<String> getConfiguredStaticFiles( @Nonnull final LinkerContext context )
  {
    return context.getConfigurationProperties()
      .stream()
      .filter( p -> "serviceworker_static_files".equals( p.getName() ) )
      .findFirst()
      .map( ConfigurationProperty::getValues )
      .orElse( Collections.emptyList() );
  }

  @Nonnull
  EmittedArtifact createPermutationMap( @Nonnull final TreeLogger logger,
                                        @Nonnull final Collection<PermutationArtifact> artifacts )
    throws UnableToCompleteException
  {
    try
    {
      final String string = PermutationsIO.serialize( collectPermutationSelectors( logger, artifacts ) );
      return emitString( logger, string, PermutationsIO.PERMUTATIONS_DESCRIPTOR_FILE_NAME );
    }
    catch ( final Exception e )
    {
      logger.log( Type.ERROR, "can not build manifest map", e );
      throw new UnableToCompleteException();
    }
  }

  @Nonnull
  List<SelectionDescriptor> collectPermutationSelectors( @Nonnull final TreeLogger logger,
                                                         @Nonnull final Collection<PermutationArtifact> artifacts )
  {
    final List<SelectionDescriptor> descriptors = new ArrayList<>();
    for ( final PermutationArtifact artifact : artifacts )
    {
      final Permutation permutation = artifact.getPermutation();
      final List<BindingProperty> calculatedBindings = new ArrayList<>();
      final Set<String> completed = new HashSet<>();

      final List<SelectionDescriptor> selectors = permutation.getSelectors();
      final SelectionDescriptor firstSelector = selectors.iterator().next();
      for ( final BindingProperty p : firstSelector.getBindingProperties() )
      {
        final String key = p.getName();
        if ( !completed.contains( key ) )
        {
          final Set<String> values = collectValuesForKey( selectors, key );
          if ( 1 == selectors.size() || values.size() > 1 )
          {
            calculatedBindings.add( new BindingProperty( key, joinValues( values ) ) );
          }
          completed.add( key );
        }
      }
      calculatedBindings.sort( ( o1, o2 ) -> o2.getComponents().length - o1.getComponents().length );
      descriptors.add( new SelectionDescriptor( permutation.getPermutationName(), calculatedBindings ) );
    }
    logger.log( Type.DEBUG, "Permutation map created with " + descriptors.size() + " descriptors." );
    return descriptors;
  }

  @Nonnull
  Set<String> collectValuesForKey( @Nonnull final List<SelectionDescriptor> selectors, @Nonnull final String key )
  {
    final Set<String> values = new HashSet<>();
    for ( final SelectionDescriptor selector : selectors )
    {
      for ( final BindingProperty property : selector.getBindingProperties() )
      {
        if ( property.getName().equals( key ) )
        {
          values.add( property.getValue() );
        }
      }
    }
    return values;
  }

  @Nonnull
  String joinValues( @Nonnull final Set<String> values )
  {
    final StringBuilder sb = new StringBuilder();
    for ( final String value : values )
    {
      if ( 0 != sb.length() )
      {
        sb.append( "," );
      }
      sb.append( value );
    }
    return sb.toString();
  }

  /**
   * Return the permutation for a single link step.
   */
  @Nullable
  Permutation calculatePermutation( @Nonnull final TreeLogger logger,
                                    @Nonnull final LinkerContext context,
                                    @Nonnull final ArtifactSet artifacts )
    throws UnableToCompleteException
  {
    Permutation permutation = null;

    for ( final SelectionInformation result : artifacts.find( SelectionInformation.class ) )
    {
      final String strongName = result.getStrongName();
      if ( null != permutation && !permutation.getPermutationName().equals( strongName ) )
      {
        throw new UnableToCompleteException();
      }
      if ( null == permutation )
      {
        permutation = new Permutation( strongName );
        final Set<String> artifactsForCompilation = getArtifactsForCompilation( context, artifacts );
        permutation.getPermutationFiles().addAll( artifactsForCompilation );
      }
      final List<BindingProperty> list = new ArrayList<>();
      for ( final SelectionProperty property : context.getProperties() )
      {
        if ( !property.isDerived() )
        {
          final String name = property.getName();
          final String value = result.getPropMap().get( name );
          if ( null != value )
          {
            list.add( new BindingProperty( name, value ) );
          }
        }
      }
      final SelectionDescriptor selection = new SelectionDescriptor( strongName, list );
      final List<SelectionDescriptor> selectors = permutation.getSelectors();
      if ( !selectors.contains( selection ) )
      {
        selectors.add( selection );
      }
    }
    if ( null != permutation )
    {
      logger.log( Type.DEBUG, "Calculated Permutation: " + permutation.getPermutationName() +
                              " Selectors: " + permutation.getSelectors() );
    }
    return permutation;
  }
}
