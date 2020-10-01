// Copyright (c) 2020 Digital Asset (Switzerland) GmbH and/or its affiliates. All rights reserved.
// SPDX-License-Identifier: Apache-2.0

import React, { ReactNode } from 'react'
import { List } from 'semantic-ui-react'
import { RealFac } from '@daml.js/realestate-facilitator';

type Props = {
  projects: RealFac.Project.Project.Project[];
  username: string;
  start: (project: RealFac.Project.Project.Project, taskId: string) => void;
  complete: (project: RealFac.Project.Project.Project, taskId: string) => void;
  ack: (project: RealFac.Project.Project.Project, taskId: string) => void;
  reject: (project: RealFac.Project.Project.Project, taskId: string) => void;
}

function StateIcon(taskState: RealFac.Project.Task.TaskState): ReactNode { 
  switch(taskState) { 
    case RealFac.Project.Task.TaskState.Pending: { 
      return <List.Icon name='pause'/>
    } 
    case RealFac.Project.Task.TaskState.InProgress: { 
      return <List.Icon name='play'/>
    } 
    case RealFac.Project.Task.TaskState.PendingDone: { 
      return <List.Icon name='question'/>
    } 
    case RealFac.Project.Task.TaskState.Done: { 
      return <List.Icon name='check'/>
    } 
  }
}

const ProjectList: React.FC<Props> = ({projects, username, start, complete, ack, reject}) => {
  return (
    <List divided relaxed verticalAlign='middle'>
      {[...projects].sort((x, y) => x.ref.localeCompare(y.ref)).map(project =>
        <List.Item key={project.ref}>
          <List.Content>
            <List.Content floated='right'><b>Service Provider</b> {project.projectParties.serviceProvider} | <b>Vendor</b> {project.projectParties.vendor} | <b>Client</b> {project.projectParties.client}</List.Content>
            <List.Content><b>{project.ref}</b></List.Content>
          </List.Content>
          <List.Content>
            <List divided relaxed>
              {[...project.tasks].map(task =>
              <List.Item key={task.descriptor.id}>
                <List.Content floated='right'>
                  <button style={{visibility:"hidden"}}>PH</button>
                  {task.state === RealFac.Project.Task.TaskState.Pending && username === project.projectParties.vendor ? <button onClick={() => start(project, task.descriptor.id)}>Start</button> : null}
                  {task.state === RealFac.Project.Task.TaskState.InProgress && username === project.projectParties.vendor ? <button onClick={() => complete(project, task.descriptor.id)}>Complete</button> : null}
                  {task.state === RealFac.Project.Task.TaskState.PendingDone && username === project.projectParties.client ? <button onClick={() => ack(project, task.descriptor.id)}>Approve</button> : null}
                  {task.state === RealFac.Project.Task.TaskState.PendingDone && username === project.projectParties.client ? <button onClick={() => reject(project, task.descriptor.id)}>Reject</button> : null}
                </List.Content>
                {StateIcon(task.state)}
                <List.Content>{task.descriptor.id}   ({task.descriptor.cashOnComplete})</List.Content>
              </List.Item>
              )}
            </List>
          </List.Content>
        </List.Item>
      )}
    </List>
  );
};

export default ProjectList;
