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

Ajax = {};

$(function() {
  // set up callback for server errors (executed on all jQuery requests)
  $(document).on("ajaxError", function(event, request, settings) {
    // ideally we'd also check that the host matches that of our backend but the host
    // part of the request URL does not seem to be available in this context,
    // so we just assume that if the URL startswith 'command/', it's an OpenRefine command
    if (settings.url.startsWith('command/')) {
      let command = settings.url.substr('command/'.length);
      let queryIndex = command.indexOf('?');
      let commandName = command.substr(0, queryIndex === -1 ? command.length : queryIndex);
      let commandParams = undefined;
      if (queryIndex != -1) {
        commandParams = command.substr(queryIndex);
      }
      if (request.status === 0) {
        alert($.i18n('core-index/connection-lost'));
      } else {
        let message = 'HTTP ' + request.status;
        if (request.responseJSON && request.responseJSON.message) {
          message = request.responseJSON.message;
        }
        alert($.i18n('core-index/internal-error-for-command', commandName, message));
      }
    }
  });
});
