To use the frontend simulation, you can enter any 15-digit Emirates ID. The simulation is designed to accept any input that follows the basic Emirates ID format and length requirement (at least 15 characters, allowing for dashes) and will proceed with the simulated login flow.

Here are the details of the mock inputs and the mock profile that gets generated:

1. What to enter in the Login Page
The login form checks for an "identifier" that has at least 5 characters (after removing dashes and spaces). However, to simulate a realistic experience, you should enter a 15-digit number formatted like an Emirates ID:

Mock Emirates ID (Example): 784-1990-1234567-1
Alternative formats: 784199012345671, user@example.com, or 971501234567 (The validation is loose enough to allow these to simulate the real portal's flexibility).
Any of these will trigger the simulated verification and approval flow.

2. The Approval Code
When you enter the ID and click "Login", the simulation will enter the "Verifying" state and then the "Approve on your phone" state.

The Code: A random 4-digit code (e.g., 4289) will be generated and displayed on the screen.
Action: In this simulation, you don't actually need to do anything with this code. The frontend 

MockAuthService
 has a built-in delay (timer) that automatically "approves" the login after a few seconds to simulate you tapping "Confirm" on your mobile app.
3. The Mock User Profile
Regardless of what Emirates ID you type in, the simulation will successfully log you in and display the following mock user data on the Digital ID Dashboard setup in the 

MockAuthService
:

First Name: Ahmed
Last Name: Al Mansoori
Full Name (English): Ahmed Khalid Al Mansoori
Email: 

ahmed.mansoori@email.ae
Mobile: +971501234567
Nationality: United Arab Emirates
Emirates ID / IDN: (This will show exactly what you typed on the login screen)
User Type: SOP1
UUID: uaepass-a1b2c3d4-e5f6-7890-abcd-ef1234567890
Gender: Male
Date of Birth: 1990-03-15
Session Token: A randomly generated mock JWT string.
How to test the complete flow:
Open the Angular app in your browser (http://localhost:4200).
Type 784-1234-5678901-2 into the input field.
Click Login.
Watch the simulated pulsing fingerprint and progress bar.
Watch the simulated 4-digit code appear.
Wait ~1.5 seconds, and it will automatically show "Identity Verified!" and redirect you to the Dashboard populated with Ahmed's details.