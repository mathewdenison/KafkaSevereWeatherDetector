# Use node image for building and running
FROM node:18-alpine

# Set the working directory inside the container
WORKDIR /app

# Copy only the package.json and package-lock.json files
COPY package*.json ./

# Install project dependencies
RUN npm install

# Copy in application source files
COPY . .

# Expose the appropriate port (React defaults to 3000)
EXPOSE 3000

# Start the development server (or production server for built apps)
CMD ["npm", "start"]