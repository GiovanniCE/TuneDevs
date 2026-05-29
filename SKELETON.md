Here is where you will explain your plan for the Walking Skeleton.

We will talk more about this in the future. In summary, the Walking Skeleton is a plan for setting up a minimal version of your tech stack. This is less than a MVP (minimum viable product) as this is not meant to be a product. It is to prove that you are able to integrate the three main components of your application: front end, back end, and database.

To complete the Skeleton you must be able to interact with your front end, have that interaction be sent to your backend, have something be stored in your database, and return a result back to the front end. This feature does not have to be particularly powerful or meaningful, but you must prove that you can communicate between each component of your application.

---

## Walking Skeleton Plan (Login Base)

### Goal
Prove end-to-end connectivity between React (front end), Java (back end), and MySQL (database) by creating a minimal login flow.

### Tech Stack
- Front end: ReactJS (simple login form)
- Back end: Java (Spring Boot REST API)
- Database: MySQL (users table)

### Starting Feature
User can enter a username and password in the React UI. The form sends a POST request to the Java API. The API stores the user in MySQL (create if not exists) and returns a success message that is shown in the UI.

### Data Flow
1. User submits login form in React.
2. React sends POST `/api/login` with JSON `{ "username": "...", "password": "..." }`.
3. Java API receives the request and writes a row to MySQL (users table).
4. Java API returns `{ "status": "ok", "message": "stored" }`.
5. React displays the response message.

### Database Schema 
- Table: `users`
	- `id` (auto increment, primary key)
	- `username` (varchar, unique)
	- `password_hash` (varchar)
	- `created_at` (timestamp)

### Endpoints 
- `POST /api/login`
	- Input: `{ "username": "...", "password": "..." }`
	- Behavior: store user (hash password), return success

### Milestones
1. Create React login page with a form and submit handler.
2. Create Spring Boot project with a single controller and service.
3. Configure MySQL connection and create `users` table.
4. Wire API to database using a simple repository.
5. Test end-to-end by submitting the form and seeing a success response.
