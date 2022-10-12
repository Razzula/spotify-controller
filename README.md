
<p  align="center">
	<img  width="128"  height="128"  src="https://github.com/Razzula/spotify-accelerometer/blob/main/app/src/main/res/drawable/logo.png">
</p>
<h1  align="center">Spotify Controller</h1>

An Android app that uses Spotify's SDK and Web API to fill the current song queue with songs appropriate for the current driving speed, from a user-selected playlist.

## Installation
No builds are currently available. To install and run, please refer to the section below.

## Building
### Prerequisites
This app uses Spotify's Android SDK and Web API.

- The SDK can be downloaded [here](https://github.com/spotify/android-sdk/releases).
- Instructions on setup can be found [here](https://developer.spotify.com/documentation/android/quick-start/)

You will also need to [register a Spotify app](https://developer.spotify.com/documentation/general/guides/authorization/app-settings/) to use both the SDK and API.

Once the prerequisites have been met, the application can be built and deployed using Android Studio.

## Usage

The app requires that Spotify is installed on the device, and that you are logged in **to a Spotify Premium account** (the app will not function for free users). In Spotify's settings, **Device Broadcast Status** must be enabled, to allow the app to know when to queue additional tracks.

This app requires the following user permissions:
-  **Location**, to calculate the vehicle velocity
-  **Ignore Battery Optimisations**, to allow the app to function when the device is on Standby or Doze.

The app will prompt and guide the user to fulfil the above requirements.

## Known Issues
- Playing the same song twice in a row causes the app to not queue a new song after the second track.
- Splash screen sometimes locks when loading

## License
### GNU GPLv3
This program is free software: you can redistribute it and/or modify it under the terms of the GNU General Public License as published by the Free Software Foundation, either version 3 of the License, or (at your option) any later version.

See [LICENSE.md](https://github.com/Razzula/spotify-accelerometer/blob/main/LICENSE.md) for details.

All Spotify trademarks, service marks, trade names, logos, domain names, and any other features of the Spotify brand (“Spotify Brand Features”) are the sole property of Spotify or its licensors. The Spotify logo is used in attribution, according to Spotify's [Design Guidelines](https://developer.spotify.com/documentation/general/design-and-branding/). **This app is not affiliated with or endorsed by Spotify in any capacity.**