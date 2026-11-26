FROM node:20-alpine

# The official node image ships an unprivileged "node" user (uid 1000).
# Everything below runs as that user, never as root.
WORKDIR /app
RUN chown node:node /app
USER node

# Copy package files and install dependencies
COPY --chown=node:node package.json package-lock.json ./
RUN npm install

# Copy only what the Vite dev server needs, instead of the whole build context
COPY --chown=node:node index.html vite.config.ts tsconfig.json tsconfig.node.json ./
COPY --chown=node:node src ./src

# Expose port 5173
EXPOSE 5173

# Start Vite dev server with host exposure
CMD ["npm", "run", "dev", "--", "--host"]
