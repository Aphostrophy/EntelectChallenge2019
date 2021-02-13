rem This starts the Entelect Visualiser 2019
rem --round-step         : integer [default 1]   : controls the round to start on, greater than 0 
rem --step-time          : float   [default 0.25]   : controls the speed of each round. The camera transition is 0.25 and is added to this value, so factor that in.
rem --max-zoom           : float   [default 7]   : controls how far the camera will zoom in. Recommended values 6-8.
rem --camera-sensitivity : float   [default 0.6] : controls how much slack the camera changes the zoom by to show all worms . Recommended values 4-6.
rem                                              : this value is unfortunately not truly linear, so results may vary throughout a match, and is very sensitive.
rem                                              : multiple tests with a match recommended for optimum results.
entelect-visualiser.exe --round-step "1" --step-time "0.25" --max-zoom "7.0" --camera-sensitivity "0.6" -screen-width 1920 -screen-height 1080 -screen-quality Ultra
