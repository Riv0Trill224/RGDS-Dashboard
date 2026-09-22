# RGDS Dashboard para Linux — prototipo

Lee CPU, RAM, batería y zonas térmicas disponibles sin root. Genera JSON Lines
con valores, fuentes y motivos de ausencia. Python 3.8+, sin paquetes pip.

```sh
python3 rgds_dashboard.py --samples 10
python3 rgds_dashboard.py --gui --geometry 640x400+1920+0
```

El segundo comando requiere Python con Tk y una sesión gráfica compatible.
Las coordenadas son un ejemplo: deben corresponder a una pantalla libre real.
No configura salidas ni instala servicios; se cierra con la X o Ctrl-C.

ROCKNIX: prototipo **sin validación en hardware**. No se presupone que la imagen
incluya Python, Tk o una sesión de ventanas; comprobarlo por SSH antes de ejecutar.
En imágenes sin esos componentes hace falta integrar un frontend con el sistema
gráfico de esa versión. No usar instrucciones apt de una distro distinta.
No modifica la instalación de ROCKNIX ni garantiza soporte de doble pantalla.

Logs en `~/.local/state/rgds-dashboard`, máximo aproximado 2 MiB por sesión,
10 sesiones. Puede cambiarse con `--log-dir`. Correo y actualizador Android
no forman parte de este prototipo. FPS Linux sigue pendiente de proveedor real.
