// IShellService.aidl
package com.francodev.appops.shell;

// Importar la clase Parcelable
import com.francodev.appops.shell.ShellResult;

// Interfaz para ejecutar comandos shell desde el UserService
interface IShellService {
    /**
     * Ejecuta un comando y retorna el resultado.
     * @param command Array de argumentos del comando
     * @return ShellResult con exitCode, stdout y stderr
     */
    ShellResult executeCommand(in String[] command);
}