# Sincronizar con tu cuenta de Google (gratis, sin servidor)

Toastune guarda un archivo `toastune-sync.json` en la carpeta oculta de la app dentro del Google Drive
de cada usuario (`drive.appdata`). Ese permiso solo ve ese archivo, no el resto del Drive, y no
requiere verificación de Google.

Para que funcione hay que registrar la app **una sola vez** en Google Cloud (gratis):

1. Entra a <https://console.cloud.google.com> y crea un proyecto llamado `Toastune`.
2. **APIs y servicios → Biblioteca** → busca **Google Drive API** → *Habilitar*.
3. **Pantalla de consentimiento OAuth** (Google Auth Platform):
   - Tipo de usuario: *Externo*; nombre `Toastune`; tu correo de contacto.
   - **Acceso a los datos → Agregar permisos** → `.../auth/drive.appdata`.
   - **Público**: pásala a *En producción* (como el permiso no es sensible no pide verificación).
     Si la dejas en *Prueba*, añade tu Gmail como usuario de prueba y el permiso caduca cada 7 días.
4. **Credenciales → Crear credenciales → ID de cliente de OAuth → Android**:
   - Nombre del paquete: `app.toastune`
   - Huella SHA-1 de tu llave de depuración:
     `keytool -list -v -keystore ~/.android/debug.keystore -alias androiddebugkey -storepass android -keypass android`
     (la variante release de este proyecto se firma con esa misma llave).
5. Ejecuta la app → Ajustes → **Cuenta de Google → Conectar**.

No hay que copiar ningún ID dentro del código: Google reconoce la app por paquete + SHA-1.

Limitación: la sincronización **suma** (une favoritos, historial y playlists de todos tus
dispositivos). Borrar algo en un dispositivo no lo borra en los demás.
