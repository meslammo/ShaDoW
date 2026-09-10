# SHADOW Runtime

The runtime is the stable boundary between clients and the SHADOW brain.

Pipeline:

`Observe -> Understand -> Plan -> Permission Check -> Execute -> Verify -> Learn`

Clients (Android voice/text/camera, desktop, home, car, automation) should call `ShadowRuntime.handle()` or an authenticated gateway adapter rather than directly depending on a provider SDK.

Provider credentials are never embedded in the Android binary. Device/home/car actions require explicit capability permission and confirmation unless a policy explicitly grants an automation rule.
