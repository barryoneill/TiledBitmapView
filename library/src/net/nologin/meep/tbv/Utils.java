/*
 *    TiledBitmapView - A library providing a view component rendered dynamically with tile data
 *    Copyright 2013 Barry O'Neill (http://meep.nologin.net/)
 *
 *    Licensed under the Apache License, Version 2.0 (the "License");
 *    you may not use this file except in compliance with the License.
 *    You may obtain a copy of the License at
 *
 *        http://www.apache.org/licenses/LICENSE-2.0
 *
 *    Unless required by applicable law or agreed to in writing, software
 *    distributed under the License is distributed on an "AS IS" BASIS,
 *    WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *    See the License for the specific language governing permissions and
 *    limitations under the License.
 */
package net.nologin.meep.tbv;

import android.os.Debug;

import java.text.DecimalFormat;

/**
 * Some people think that such utility classes are a bad idea.  Some people don't.
 */
public class Utils {

    /**
     * Logging Constant, all messages have the tag "TBV"
     */
    public static final String LOG_TAG = "TBV";

    // create the formatter once
    private static final DecimalFormat df = new DecimalFormat();
    static {
        df.setMaximumFractionDigits(2);
        df.setMinimumFractionDigits(2);
    }


    /**
     * Get some debug information about memory status, used in the information box when debug is
     * enabled in the view.  This has a bit of a negative impact on performance, but is worth it
     * to see how the heap gets used up as you scroll around in a poorly written provider.
     *
     * @return A string of the format:<br/>
     *         <i>[rt_alloced]/[rt_available]MB, nat([native_allocated]/[native_avail]MB)</i>
     */
    public static String getMemDebugString() {

        /* The following based on http://stackoverflow.com/a/3238945/276183
         * Excessive string creation here, but this is a debug method.  */

        String nat_allo = df.format((double) Debug.getNativeHeapAllocatedSize() / 1048576.0);
        String nat_avail = df.format((double) Debug.getNativeHeapSize() / 1048576.0);

        String mem_alloc = df.format((double) (Runtime.getRuntime().totalMemory() / 1048576));
        String mem_avail = df.format((double) (Runtime.getRuntime().maxMemory() / 1048576));

        return String.format("%s/%sMB, nat(%s/%sMB)", mem_alloc, mem_avail, nat_allo, nat_avail);

    }

    /**
     * @return <code>true</code> if the ratio of available memory starts getting low.  Used for
     *         some debug render candy (eg turn the text red)
     */
    public static boolean isHeapAlmostFull() {

        return (Runtime.getRuntime().totalMemory() / (double) Runtime.getRuntime().maxMemory()) > 0.7;

    }


}
