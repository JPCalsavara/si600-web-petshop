FROM node:20-alpine

WORKDIR /app

# Copy package files and install dependencies
COPY package*.json ./
RUN npm install

# Copy source code
COPY . .

# Expose port 5173
EXPOSE 5173

# Start Vite dev server with host exposure
CMD ["npm", "run", "dev", "--", "--host"]
