/*

Copyright 2010, Google Inc.
All rights reserved.

Redistribution and use in source and binary forms, with or without
modification, are permitted provided that the following conditions are
met:

    * Redistributions of source code must retain the above copyright
notice, this list of conditions and the following disclaimer.
    * Redistributions in binary form must reproduce the above
copyright notice, this list of conditions and the following disclaimer
in the documentation and/or other materials provided with the
distribution.
    * Neither the name of Google Inc. nor the names of its
contributors may be used to endorse or promote products derived from
this software without specific prior written permission.

THIS SOFTWARE IS PROVIDED BY THE COPYRIGHT HOLDERS AND CONTRIBUTORS
"AS IS" AND ANY EXPRESS OR IMPLIED WARRANTIES, INCLUDING, BUT NOT
LIMITED TO, THE IMPLIED WARRANTIES OF MERCHANTABILITY AND FITNESS FOR
A PARTICULAR PURPOSE ARE DISCLAIMED. IN NO EVENT SHALL THE COPYRIGHT
OWNER OR CONTRIBUTORS BE LIABLE FOR ANY DIRECT, INDIRECT, INCIDENTAL,
SPECIAL, EXEMPLARY, OR CONSEQUENTIAL DAMAGES (INCLUDING, BUT NOT
LIMITED TO, PROCUREMENT OF SUBSTITUTE GOODS OR SERVICES; LOSS OF USE,           
DATA, OR PROFITS; OR BUSINESS INTERRUPTION) HOWEVER CAUSED AND ON ANY           
THEORY OF LIABILITY, WHETHER IN CONTRACT, STRICT LIABILITY, OR TORT
(INCLUDING NEGLIGENCE OR OTHERWISE) ARISING IN ANY WAY OUT OF THE USE
OF THIS SOFTWARE, EVEN IF ADVISED OF THE POSSIBILITY OF SUCH DAMAGE.

*/

package org.openrefine.browsing.facets;

import java.util.Map;
import java.util.Set;


/**
 * Interface of facets.
 */
public interface Facet  {
    
    /**
     * An initial facet state for this facet, which can
     * then be used to scan the table and ingest statistics
     * about rows or records.
     */
    public FacetState getInitialFacetState();
    
    /**
     * An aggregator used to populate the facet state for this facet.
     * It should accept the initial state returned by {@link getInitialFacetState}.
     */
    public FacetAggregator<?> getAggregator();
    
    /**
     * Returns all the information necessary to render the
     * facet in the UI (aggregation statistics and configuration
     * combined).
     */
    public FacetResult getFacetResult(FacetState state);
    
    
    /**
     * The columns this facet depends on.
     * 
     * @return null if dependent columns cannot be extracted, in which
     * case it should be assumed that the facet potentially depends on all
     * columns.
     */
    public Set<String> getColumnDependencies();
    
    /**
     * Updates the facet config after a renaming of columns.
     * 
     * @return null if the update could not be performed, or the new
     * facet config if the update could be performed.
     */
    public FacetConfig renameColumnDependencies(Map<String, String> substitutions);
}
