FROM node:24-alpine
WORKDIR /service
COPY --chown=node:node package.json core.mjs plan-safety.mjs location-context.mjs girlfriend-mode.mjs pilot-training.mjs contact-guidance.mjs autopilot.mjs history-learning.mjs server.mjs ./
USER node
ENV NODE_ENV=production HOST=0.0.0.0 TRUST_TLS_PROXY=yes
EXPOSE 8781
CMD ["node", "server.mjs"]
