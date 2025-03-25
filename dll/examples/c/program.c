/**
 * Copyright © 2023 JADAPTIVE Limited (support@jadaptive.com)
 *
 * Permission is hereby granted, free of charge, to any person obtaining a copy of this
 * software and associated documentation files (the “Software”), to deal in the Software
 * without restriction, including without limitation the rights to use, copy, modify,
 * merge, publish, distribute, sublicense, and/or sell copies of the Software, and to
 * permit persons to whom the Software is furnished to do so, subject to the following
 * conditions:
 *
 * The above copyright notice and this permission notice shall be included in all copies
 * or substantial portions of the Software.
 *
 * THE SOFTWARE IS PROVIDED “AS IS”, WITHOUT WARRANTY OF ANY KIND, EXPRESS OR IMPLIED,
 * INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY, FITNESS FOR A
 * PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE AUTHORS OR COPYRIGHT
 * HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER LIABILITY, WHETHER IN AN ACTION
 * OF CONTRACT, TORT OR OTHERWISE, ARISING FROM, OUT OF OR IN CONNECTION WITH THE
 * SOFTWARE OR THE USE OR OTHER DEALINGS IN THE SOFTWARE.
 */
 #include <stdio.h>
 #include <stdlib.h>
 #include <string.h>

 #include "lbvdll.h"

 int main(int argc, char **argv) {
 if (argc != 3) {
     fprintf(stderr, "Usage: %s [up|down] <wgfile>\n", argv[0]);
     exit(1);
 }

 graal_isolate_t *isolate = NULL;
 graal_isolatethread_t *thread = NULL;

 if (graal_create_isolate(NULL, &isolate, &thread) != 0) {
     fprintf(stderr, "initialization error\n");
     return 1;
 }

 if(strcmp("up", argv[1]) == 0) {
	 long long int hndl = up(thread, argv[2], 0, 0);

	 printf("Handle: %lli\n", hndl);
	 if(hndl == 0) {
		 printf("Code: %i\n", get_error_code(thread));
	 }
 }
 else if(strcmp("down", argv[1]) == 0) {
	 stop(thread, argv[2], 0, 0);
 }
 else {
     fprintf(stderr, "Usage: %s [up|down] <wgfile>\n", argv[0]);
     graal_detach_all_threads_and_tear_down_isolate(thread);
     exit(1);
 }

 fprintf(stderr, "tearing down\n");
 graal_detach_all_threads_and_tear_down_isolate(thread);
 }
