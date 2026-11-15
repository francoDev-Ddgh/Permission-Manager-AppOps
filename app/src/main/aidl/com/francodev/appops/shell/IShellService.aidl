// IShellService.aidl
package com.francodev.appops.shell;

import com.francodev.appops.shell.ShellResult;

interface IShellService {

    // Código de transacción fijo que usa el servidor de Shizuku para destruir el UserService.
    void destroy() = 16777114;

    ShellResult executeCommand(in String[] command) = 1;
}