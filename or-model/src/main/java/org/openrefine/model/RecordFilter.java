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

package org.openrefine.model;

import java.io.Serializable;
import java.util.List;

/**
 * Interface for judging if a particular record matches or doesn't match some
 * particular criterion, such as a facet constraint.
 */
public interface RecordFilter extends Serializable {
    public boolean filterRecord(Record record);
    
    /**
     * A filter which accepts all records.
     */
    public static RecordFilter ANY_RECORD = new RecordFilter() {

        private static final long serialVersionUID = 1337763887735565917L;

        @Override
        public boolean filterRecord(Record record) {
            return true;
        }
        
    };

    /**
     * A record filter which evaluates to true when all the supplied record
     * filters do.
     */
    public static RecordFilter conjunction(List<RecordFilter> recordFilters) {
        if (recordFilters.isEmpty()) {
            return RecordFilter.ANY_RECORD;
        } else {
            return new RecordFilter() {  
                private static final long serialVersionUID = -7387688969915555389L;

                @Override
                public boolean filterRecord(Record record) {
                    return recordFilters.stream().allMatch(f -> f.filterRecord(record));
                }
            };
        }
    }

    public static RecordFilter negate(RecordFilter filter) {
        return new RecordFilter() {
            private static final long serialVersionUID = 5699141718569021249L;

            @Override
            public boolean filterRecord(Record record) {
                return !filter.filterRecord(record);
            }
            
        };
    }
}
