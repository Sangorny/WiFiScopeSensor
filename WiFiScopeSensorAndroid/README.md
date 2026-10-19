
# WiFiScope Sensor Android 0.1

Aplicación Android que convierte el móvil en un sensor remoto para WiFiScope.

## Funciones

- Escaneo de redes Wi‑Fi.
- SSID.
- BSSID.
- RSSI real informado por Android en dBm.
- Frecuencia.
- Canal aproximado.
- Capacidades/seguridad.
- Muestra las redes en pantalla.
- Servidor HTTP local en el puerto 8765.
- Endpoint JSON:

    http://IP_DEL_MOVIL:8765/scan

- Endpoint de estado:

    http://IP_DEL_MOVIL:8765/status

## Ejemplo de respuesta

```json
{
  "app": "WiFiScope Sensor",
  "version": "0.1.0",
  "timestamp": 1792420000000,
  "deviceIp": "192.168.1.50",
  "networks": [
    {
      "ssid": "ALMACEN_WIFI",
      "bssid": "aa:bb:cc:dd:ee:ff",
      "rssi": -58,
      "frequency": 5180,
      "channel": 36,
      "capabilities": "[WPA2-PSK-CCMP][ESS]"
    }
  ]
}
```

## Uso

1. Instala la aplicación en el móvil.
2. Concede los permisos solicitados.
3. Activa Wi‑Fi.
4. Activa Ubicación si Android lo solicita.
5. Conecta móvil y portátil a una red donde puedan verse entre sí.
6. Pulsa `Escanear Wi‑Fi ahora`.
7. Pulsa `Iniciar servidor`.
8. En pantalla aparecerá una dirección como:

    http://192.168.1.50:8765/scan

Esa será la dirección que se configurará en WiFiScope para recibir las mediciones.

## Compilar el APK con Android Studio

1. Abre esta carpeta con Android Studio.
2. Espera a que sincronice Gradle.
3. Build > Build App Bundle(s) / APK(s) > Build APK(s).
4. El APK de depuración quedará normalmente en:

    app/build/outputs/apk/debug/app-debug.apk

## Permisos de Android

Android trata los resultados de escaneo Wi‑Fi como información sensible.

Según la versión del sistema pueden ser necesarios:

- Ubicación precisa.
- Dispositivos cercanos.
- Ubicación del sistema activada.

Esto afecta al móvil, no al portátil corporativo.

## Limitación

Android limita la frecuencia de los escaneos Wi‑Fi activos. El sensor usa los resultados más recientes que el sistema le permite obtener. Para un site survey, se recomienda esperar unos segundos entre puntos de medición.
