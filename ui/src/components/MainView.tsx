// Copyright (c) 2020 Digital Asset (Switzerland) GmbH and/or its affiliates. All rights reserved.
// SPDX-License-Identifier: Apache-2.0

import React, { useMemo } from 'react';
import { Container, Grid, Header, Icon, Segment, Divider } from 'semantic-ui-react';
import { RealFac } from '@daml.js/realestate-facilitator';
import { useParty, useStreamQuery, useLedger } from '@daml/react';
import ProjectList from './ProjectList';
import CashAccountList from './CashAccountList';

// USERS_BEGIN
const MainView: React.FC = () => {
  const username = useParty();
// USERS_END

  const allProjects = useStreamQuery(RealFac.Project.Project.Project).contracts
  const projects = useMemo(() =>
    allProjects
    .map(project => project.payload),
    [allProjects]);
  const allCashAccounts = useStreamQuery(RealFac.CashAccount.CashAccount.CashAccount).contracts
  const cashAccounts = useMemo(() =>
    allCashAccounts
    .map(x => x.payload),
    [allCashAccounts]);

  const ledger = useLedger();

  const ack = async (project: RealFac.Project.Project.Project, taskId: string): Promise<boolean> => {
    try {
      await ledger.exerciseByKey(RealFac.Project.Project.Project.Ack, {_1:project.projectParties.serviceProvider,_2:project.ref}, {taskId:taskId});
      return true;
    } catch (error) {
      alert("Unknown error:\n" + JSON.stringify(error));
      return false;
    }
  }
    
  const reject = async (project: RealFac.Project.Project.Project, taskId: string): Promise<boolean> => {
    try {
      await ledger.exerciseByKey(RealFac.Project.Project.Project.Reject, {_1:project.projectParties.serviceProvider,_2:project.ref}, {taskId:taskId});
      return true;
    } catch (error) {
      alert("Unknown error:\n" + JSON.stringify(error));
      return false;
    }
  }
    
  const start = async (project: RealFac.Project.Project.Project, taskId: string): Promise<boolean> => {
    try {
      await ledger.exerciseByKey(RealFac.Project.Project.Project.Start, {_1:project.projectParties.serviceProvider,_2:project.ref}, {taskId:taskId});
      return true;
    } catch (error) {
      alert("Unknown error:\n" + JSON.stringify(error));
      return false;
    }
  }
    
  const complete = async (project: RealFac.Project.Project.Project, taskId: string): Promise<boolean> => {
    try {
      await ledger.exerciseByKey(RealFac.Project.Project.Project.Complete, {_1:project.projectParties.serviceProvider,_2:project.ref}, {taskId:taskId});
      return true;
    } catch (error) {
      alert("Unknown error:\n" + JSON.stringify(error));
      return false;
    }
  }
    
  return (
    <Container>
      <Grid centered>
        <Grid.Row stretched>
          <Grid.Column>
            <Segment>
              <Header as='h2'>
                <Icon name='lock' />
                <Header.Content>
                  Cash Accounts
                  <Header.Subheader>All available cash-accounts.</Header.Subheader>
                </Header.Content>
              </Header>
              <Divider />
              <CashAccountList
                cashAccounts={cashAccounts}
              />
            </Segment>
            <Segment>
              <Header as='h2'>
                <Icon name='sitemap' />
                <Header.Content>
                  Projects
                  <Header.Subheader>All available projects.</Header.Subheader>
                </Header.Content>
              </Header>
              <Divider />
              <ProjectList
                projects={projects}
                username={username}
                start={start}
                complete={complete}
                ack={ack}
                reject={reject}
              />
            </Segment>
          </Grid.Column>
        </Grid.Row>
      </Grid>
    </Container>
  );
}

export default MainView;
