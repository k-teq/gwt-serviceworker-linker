package org.realityforge.gwt.serviceworker;

import java.util.*;

public class Permutation
{
  public static final String PERMUTATION_MANIFEST_FILE_ENDING = ".appcache";
  private final String _permutationName;
  private final List<SelectionDescriptor> _selectors = new ArrayList<>();
  private final HashSet<String> _permutationFiles = new HashSet<>();

  public Permutation( final String permutationName )
  {
    _permutationName = permutationName;
  }

  public int hashCode()
  {
    return _permutationName.hashCode();
  }

  @Override
  public boolean equals( final Object obj )
  {
    if ( !( obj instanceof Permutation ) )
    {
      return false;
    }
    final Permutation other = (Permutation) obj;
    return other._permutationName.equals( _permutationName );
  }

  public String getPermutationName()
  {
    return _permutationName;
  }

  public Set<String> getPermutationFiles()
  {
    return _permutationFiles;
  }

  public List<SelectionDescriptor> getSelectors()
  {
    return _selectors;
  }
}
